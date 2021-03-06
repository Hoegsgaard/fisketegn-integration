package dk.fishery.fisketegn.processors;

import com.mongodb.BasicDBObject;
import dk.fishery.fisketegn.model.User;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class UpdateUserProcessor implements Processor {
  @Override
  public void process(Exchange exchange) throws Exception {
    BasicDBObject input = exchange.getIn().getBody(BasicDBObject.class);
    User newUserInfo = (User) exchange.getProperty("user");
    input.put("cpr",newUserInfo.getCpr());
    input.put("firstName",newUserInfo.getFirstName());
    input.put("lastName", newUserInfo.getLastName());
    input.put("email",newUserInfo.getEmail());
    input.put("address", newUserInfo.getAddress());
    input.put("zipCode", newUserInfo.getZipCode());
    input.put("country",newUserInfo.getCountry());
    exchange.getIn().setBody(input);
  }
}
