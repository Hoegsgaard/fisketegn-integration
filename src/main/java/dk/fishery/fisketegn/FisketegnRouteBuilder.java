package dk.fishery.fisketegn;

import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import dk.fishery.fisketegn.model.User;
import dk.fishery.fisketegn.processors.checkPassword;
import dk.fishery.fisketegn.processors.hashPassword;
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


      rest("/db/").description("database test")
              .id("db-route")
              .get()
              .to("direct:mongoRoute")

              .post()
              .to("direct:insert");
      /*from("direct:mongoRoute")
              .streamCaching()
              .setBody(simple(""))
              .to("mongodb:fisketegnDb?database=test&collection=testCollection&operation=findAll")
              .setHeader("Content-Type",constant("application/json; charset=UTF-8"));*/
      from("direct:insert")
        .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=insert");

      from("direct:mongoRoute")
              .streamCaching()
              .setBody(simple(""))
              .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findAll")
              .setHeader("Content-Type",constant("application/json; charset=UTF-8"));



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


      .post("/bean")
      .type(MyBean.class)
      .to("direct:remoteService");

      from("direct:findOrCreateUser")
      .streamCaching()
      .routeId("findOrCreateUser")
      .setProperty("oldBody", simple("${body}"))
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
              .log("Bruger eksistere")
              // .to("direct:payment")
              // .to("direct:createLicense")
        .otherwise()
          .log("Bruger eksistere endnu ikke")
          .to("direct:createUser");

      from("direct:createUser")
        //.to("direct:payment")
        .process(new hashPassword())
        // Gem bruger i DB
        .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=insert");
        //.to("direct:createLicense");

     // from("direct:createLicense")
        // Opret fisketegn til brugeren
        // Retuner fisketegn til brugeren
        // Send fisketegn på mail, måske en .process();

      //from("direct:payment");

      from("direct:doesUserExist")
      .setProperty("oldBody", simple("${body}"))
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
      .choice()
        .when(header(RESULT_PAGE_SIZE).isGreaterThan(0))
        .log("Bruger eksisterer")
        .process(new checkPassword())
        .choice()
          .when(body().isEqualTo(true))
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
            .setBody(simple("User is authenticated"))
          .otherwise()
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
            .setBody(simple("Wrong username or password"))
        .otherwise()
          .log("Bruger eksistere endnu ikke")
          .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
          .setBody(simple("Wrong username or password"));


      from("direct:remoteService")
      .streamCaching()
      .routeId("direct-route")
      .tracing()
      .process(new Processor() {
        public void process(Exchange exchange) throws Exception {
          MyBean bodyIn = (MyBean) exchange.getIn().getBody();
          ExampleServices.example(bodyIn);
          exchange.getIn().setBody(bodyIn);
        }
      })
      //.log(">>> ${body.name}")
      .log(">>> ${body.id}")
      .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(201));
    }
}


