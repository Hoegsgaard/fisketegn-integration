package dk.fishery.fisketegn;

import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import dk.fishery.fisketegn.model.License;
import dk.fishery.fisketegn.model.User;
import dk.fishery.fisketegn.processors.*;
import io.swagger.util.Json;
import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mongodb.CamelMongoDbException;
import org.apache.camel.component.mongodb.MongoDbConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.language.xpath.XPathBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.apache.camel.component.mongodb.MongoDbConstants.RESULT_PAGE_SIZE;

@Component
public class FisketegnRouteBuilder extends RouteBuilder {

  @Value("${fisketegn.api.path}")
  String contextPath;

  @Value("${server.port}")
  String serverPort;

  @Value("${jwtSecure.key}")
  String jwtKey;

    @Override
    public void configure() throws Exception {
      CamelContext context = new DefaultCamelContext();

      onException(IOException.class)
              .log("?")
              .handled(true)
              .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
              .setBody(simple("nothing found in query"));
      onException(RuntimeCamelException.class)
              .log("Camel exception!")
              .handled(true)
              .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
              .setBody(simple("camel exception!"));


      restConfiguration()
      .contextPath(contextPath)
      .port(serverPort)
      .enableCORS(true)
      .apiContextPath("/api-doc")
      .apiProperty("api.title", "rest api")
      .apiProperty("api.version", "v1")
      .apiProperty("cors", "true")
      .apiContextRouteId("doc-api")
      .component("servlet")
      .bindingMode(RestBindingMode.json)
      .dataFormatProperty("prettyPrint", "true");


      rest("/api/").description("dk.fishery.SpringBootStarter Rest")
      .id("api-route")
      .enableCORS(true)
      .produces(MediaType.APPLICATION_JSON)
      .consumes(MediaType.APPLICATION_JSON)
      .bindingMode(RestBindingMode.json)

      // AUTH
      .post("/login")
      .type(User.class)
      .to("direct:doesUserExist")

      .post("/validateToken")
      .to("direct:validateToken")

      .post("/buyLicense")
      .type(User.class)
      .to("direct:findOrCreateUser")

      // USER
      .put("/user")
      .type(User.class)
      .to("direct:updateUser")

      .get("/user")
      .to("direct:getUser")

      .delete("/user")
      .to("direct:deleteUser")

      .post("/updatePassword")
      .to("direct:updatePassword");

      // Admin



      // Auth Endpoints
      from("direct:validateToken")
      .setProperty("tokenKey", constant(jwtKey))
      .process(new validateTokenProcessor());

      from("direct:doesUserExist")
      .setProperty("oldBody", simple("${body}"))
      .setProperty("tokenKey", constant(jwtKey))
      .process(new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
          User user = exchange.getIn().getBody(User.class);
          String email = user.getEmail();
          Bson criteria = Filters.eq("email", email);
          exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
        }
      })
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")

      .setProperty("newBody", simple("${body}"))
      .choice()
        .when(header(RESULT_PAGE_SIZE).isGreaterThan(0))
          .log("Bruger eksisterer")
          .process(new checkPasswordProcessor())
          .choice()
            .when(exchangeProperty("userAuth").isEqualTo(true))
              .process(new generateTokenProcessor())
              .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
            .otherwise()
              .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
              .setBody(simple("Wrong username or password"))
          .endChoice()
        .otherwise()
          .log("Bruger eksistere endnu ikke")
          .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
          .setBody(simple("User dont exist"));

      // FIND OR CREATE USER
      from("direct:findOrCreateUser")
      .streamCaching()
      .routeId("findOrCreateUser")
      .setProperty("oldBody", simple("${body}"))
      .setProperty("tokenKey", constant(jwtKey))
      // HUSK AT TRÆKKE FISKETEGNSTYPE UD
      .process(
      new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
          User user = exchange.getIn().getBody(User.class);
          String email = user.getEmail();
          Bson criteria = Filters.eq("email", email);
          exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
        }
      })
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
      .choice()
      .when(header(RESULT_PAGE_SIZE).isGreaterThan(0))
      .setProperty("newUser", simple("${body}"))
      .log("Bruger eksistere")
      .process(new generateTokenProcessor())
      // .to("direct:payment")
       .to("direct:createLicense")
      .otherwise()
      .log("Bruger eksistere endnu ikke")
      .to("direct:createUser");

      from("direct:createUser")
      //.to("direct:payment")
      .process(new hashPasswordProcessor())
      // Gem bruger i DB
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=insert")
      .process(new generateTokenProcessor())
      .to("direct:createLicense");

      from("direct:createLicense")
      .process(new createLicenseProcessor())
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Licenses&operation=insert")
      .process(
              new Processor() {
                @Override
                public void process(Exchange exchange) throws Exception {
                  User input = (User) exchange.getProperty("oldBody");
                  Bson criteria = Filters.eq("email", input.getEmail());
                  exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
                }
              })
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
      .process(new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
          BasicDBObject user = exchange.getIn().getBody(BasicDBObject.class);
          ArrayList<String> licenses = (ArrayList<String>) user.get("licenses");
          if(licenses == null){
            licenses = new ArrayList<>();
          }
          licenses.add((String) exchange.getProperty("licenseID"));
          user.put("licenses", licenses);
          //user.addLicense((String) exchange.getProperty("licenseID"));
          exchange.getIn().setBody(user);
        }
      })
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=save");

      // Opret fisketegn til brugeren
      // Retuner fisketegn til brugeren
      // Send fisketegn på mail, måske en .process();

      //from("direct:payment");

      // User endpoints

      // UPDATE USER
      from("direct:updateUser")
      .setProperty("tokenKey", constant(jwtKey))
      .process(new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
          User user = exchange.getIn().getBody(User.class);
          exchange.setProperty("user", user);
        }
      })
      .process(new validateTokenProcessor())
      .choice()
      .when(exchangeProperty("tokenIsValidated").isEqualTo(true))
      .process(new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
          exchange.getIn().setBody(exchange.getProperty("user"));
        }
      })
      .process(new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
          String email = (String) exchange.getProperty("userEmail");
          Bson criteria = Filters.eq("email", email);
          exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
        }
      })
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
      .process(new updateUserProcessor())
      .setProperty("newUser", simple("${body}"))
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=save")
      .setBody(exchangeProperty("newUser"))
      .process(new generateTokenProcessor());

      // GET USER
      from("direct:getUser")
      .setProperty("tokenKey", constant(jwtKey))
      .process( new validateTokenProcessor())
      .choice()
      .when(exchangeProperty("tokenIsValidated").isEqualTo(true))
      .process(
      new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
          String email = (String) exchange.getProperty("userEmail");
          Bson criteria = Filters.eq("email", email);
          exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
        }
      })
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
      .process(new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
          BasicDBObject user = exchange.getIn().getBody(BasicDBObject.class);
          user.removeField("password");
          user.removeField("role");
          user.removeField("_id");
          exchange.getIn().setBody(user);
        }
      });

      // DELETE USER
      from("direct:deleteUser")
      .setProperty("tokenKey", constant(jwtKey))
      .process(new validateTokenProcessor())
      .choice()
        .when(exchangeProperty("tokenIsValidated").isEqualTo(true))
          .process(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
              String email = (String) exchange.getProperty("userEmail");
              Bson criteria = Filters.eq("email", email);
              exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
            }
          })
          .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
          .choice()
            .when(header(RESULT_PAGE_SIZE).isGreaterThan(0))
              .process(new Processor() {
                @Override
                public void process(Exchange exchange) throws Exception {
                  BasicDBObject user = exchange.getIn().getBody(BasicDBObject.class);
                  exchange.getIn().setBody(user);
                }
              })
              .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=remove")
              .setBody(simple("User deleted"))
              .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
            .otherwise()
              .setBody(simple("User do not exsist"))
              .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401));

      from("direct:updatePassword")
      .process(new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
          LinkedHashMap<String,String> password = exchange.getIn().getBody(LinkedHashMap.class);
          exchange.setProperty("newPassword",password.get("password"));
        }
      })
      .setProperty("tokenKey", constant(jwtKey))
      .process(new validateTokenProcessor())
      .choice()
      .when(exchangeProperty("tokenIsValidated").isEqualTo(true))
      .process(
      new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
          String email = (String) exchange.getProperty("userEmail");
          Bson criteria = Filters.eq("email", email);
          exchange.getIn().setHeader(MongoDbConstants.CRITERIA, criteria);
        }
      })
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
      .process(new updatePasswordProcessor())
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=save")
      .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
      .setBody(simple("Password is updated"))
      .otherwise()
      .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
      .setBody(simple("Token invalid"));

      // Admin Endpoints



    }
}


