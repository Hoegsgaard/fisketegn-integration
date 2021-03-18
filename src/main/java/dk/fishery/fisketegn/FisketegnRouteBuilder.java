package dk.fishery.fisketegn;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.rest.RestBindingMode;
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

      rest("/api/").description("dk.fishery.SpringBootStarter Rest")
      .id("api-route")
      .post("/bean")
      .produces(MediaType.APPLICATION_JSON)
      .consumes(MediaType.APPLICATION_JSON)
      .bindingMode(RestBindingMode.auto)
      .type(MyBean.class)
      .enableCORS(true)
      .to("direct:remoteService");

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


