package dk.fishery.fisketegn.processors;

import com.mongodb.BasicDBObject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class FlagLicenseDeletedProcessor implements Processor {
  @Override
  public void process(Exchange exchange) throws Exception {
    BasicDBObject input = exchange.getIn().getBody(BasicDBObject.class);
    input.put("deletedFlag",!((boolean) input.get("deletedFlag")));
    exchange.getIn().setBody(input);
  }
}
