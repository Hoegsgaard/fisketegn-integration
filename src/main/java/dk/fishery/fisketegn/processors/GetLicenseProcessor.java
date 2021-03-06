package dk.fishery.fisketegn.processors;

import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.bson.conversions.Bson;

import java.util.ArrayList;

public class GetLicenseProcessor implements Processor {
  @Override
  public void process(Exchange exchange) throws Exception {
    BasicDBObject input = exchange.getIn().getBody(BasicDBObject.class);
    ArrayList licensesList = (ArrayList) input.get("licenses");
    ArrayList<Bson> bsons = new ArrayList<>();
    for (Object o : licensesList) {
      bsons.add(Filters.eq("licenseID", o));
    }
    exchange.setProperty("bsons", bsons);
  }
}
