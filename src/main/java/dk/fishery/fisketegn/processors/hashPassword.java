package dk.fishery.fisketegn.processors;

import dk.fishery.fisketegn.model.User;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.mindrot.jbcrypt.BCrypt;

public class hashPassword implements Processor {
  @Override
  public void process(Exchange exchange) throws Exception {
    User user = (User) exchange.getProperty("oldBody");
    String salt = BCrypt.gensalt(10);
    String hashedPassword = BCrypt.hashpw(user.getPassword(), salt);
    user.setPassword(hashedPassword);
    exchange.getIn().setBody(user);
  }

  public void tester(Exchange exchange) throws Exception {

  }
}
