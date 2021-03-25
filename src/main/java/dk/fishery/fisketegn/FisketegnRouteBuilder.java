package dk.fishery.fisketegn;

import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import dk.fishery.fisketegn.model.User;
import dk.fishery.fisketegn.processors.checkPassword;
import dk.fishery.fisketegn.processors.generateToken;
import dk.fishery.fisketegn.processors.hashPassword;
import dk.fishery.fisketegn.processors.validateToken;
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
      .bindingMode(RestBindingMode.auto)

      .post("/buyLicense")
      .type(User.class)
      .to("direct:findOrCreateUser")

      .post("/login")
      .type(User.class)
      .to("direct:doesUserExist")

      .post("/validateToken")
      .to("direct:validateToken");

      from("direct:validateToken")
      .setProperty("tokenKey", constant(jwtKey))
      .process(new validateToken());

      from("direct:findOrCreateUser")
      .streamCaching()
      .routeId("findOrCreateUser")
      .setProperty("oldBody", simple("${body}"))
      .setProperty("tokenKey", constant(jwtKey))
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
              .process(new generateToken())
              // .to("direct:payment")
              // .to("direct:createLicense")
        .otherwise()
          .log("Bruger eksistere endnu ikke")
          .to("direct:createUser");

      from("direct:createUser")
        //.to("direct:payment")
        .process(new hashPassword())
        // Gem bruger i DB
        .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=insert")
        .process(new generateToken());
        //.to("direct:createLicense");

     // from("direct:createLicense")
        // Opret fisketegn til brugeren
        // Retuner fisketegn til brugeren
        // Send fisketegn på mail, måske en .process();

      //from("direct:payment");

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
        .process(new checkPassword())
        .choice()
          .when(exchangeProperty("userAuth").isEqualTo(true))
            .process(new generateToken())
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
          .otherwise()
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
            .setBody(simple("Wrong username or password"))
        .otherwise()
          .log("Bruger eksistere endnu ikke")
          .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
          .setBody(simple("Wrong username or password"));
    }
}


