package dk.fishery.fisketegn.processors;

import com.mongodb.BasicDBObject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.mindrot.jbcrypt.BCrypt;

public class UpdatePasswordProcessor implements Processor {
  @Override
  public void process(Exchange exchange) throws Exception {
    String newPassword = (String) exchange.getProperty("newPassword");
    BasicDBObject user = exchange.getIn().getBody(BasicDBObject.class);
    String salt = BCrypt.gensalt(10);
    String hashedPassword = BCrypt.hashpw(newPassword, salt);
    user.put("password",hashedPassword);
    exchange.getIn().setBody(user);
  }
}
