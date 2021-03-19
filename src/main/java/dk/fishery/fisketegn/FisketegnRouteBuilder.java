package dk.fishery.fisketegn;

import com.mongodb.client.model.Filters;
import dk.fishery.fisketegn.model.User;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mongodb.MongoDbConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.rest.RestBindingMode;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.ws.rs.core.MediaType;


@Component
public class FisketegnRouteBuilder extends RouteBuilder {

  @Value("${fisketegn.api.path}")
  String contextPath;

  @Value("${server.port}")
  String serverPort;

    @Override
    public void configure() throws Exception {
      CamelContext context = new DefaultCamelContext();

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

      .post("/bean")
      .type(MyBean.class)
      .to("direct:remoteService");

      from("direct:findOrCreateUser")
      .setHeader(MongoDbConstants.CRITERIA, new Expression() {
        @Override
        public <T> T evaluate(Exchange exchange, Class<T> type) {
          User user = (User) exchange.getIn().getBody();
          String email = user.getEmail();
          Bson criteria = Filters.eq("email", email);
          return exchange.getContext().getTypeConverter().convertTo(type, criteria);
        }
      })
      .to("mongodb:fisketegnDb?database=Fisketegn&collection=Users&operation=findOneByQuery");

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


