package dk.fishery.fisketegn.processors;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.util.LinkedHashMap;

public class SavePropertyProcessor implements Processor {
  @Override
  public void process(Exchange exchange) throws Exception {
    LinkedHashMap<String,String> prop = exchange.getIn().getBody(LinkedHashMap.class);
    exchange.setProperty("usersEmail",prop.get("email"));
    exchange.setProperty("newPassword",prop.get("password"));
    exchange.setProperty("oldPassword",prop.get("oldPassword"));
    exchange.setProperty("newRole",prop.get("role"));
    exchange.setProperty("licenseID",prop.get("licenseID"));
  }
}
