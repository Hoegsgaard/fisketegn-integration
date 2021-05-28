package dk.fishery.fisketegn.processors;

import com.mongodb.BasicDBObject;
import org.apache.camel.Exchange;
import org.apache.camel.util.json.JsonObject;
import org.bson.Document;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class UpdateLicenseProcessor implements org.apache.camel.Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
        LinkedHashMap<String,String> prop = (LinkedHashMap) exchange.getProperty("licenseID");
        Document input = (Document) exchange.getProperty("user");
        String licenseID = prop.get("licenseID");
        ArrayList<String> listOfLicenses = (ArrayList<String>) input.get("licenses");
        if(listOfLicenses.contains(licenseID)){
            //License belongs to user
            ArrayList body = (ArrayList) exchange.getIn().getBody();
            Document doc = (Document) body.get(0);
            BasicDBObject output = new BasicDBObject(doc);
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            LocalDate endDate;
            endDate = LocalDate.parse(output.getString("endDate"),dtf);
            if(output.get("type").equals("d")){
                endDate = endDate.plusDays(1);
            }else if(output.get("type").equals("w")){
                endDate = endDate.plusDays(7);
            }else if(output.get("type").equals("y")){
                endDate = endDate.plusDays(365);
            }
            output.put("endDate", endDate.format(dtf));
            exchange.setProperty("licenseNumber", output.get("licenseNumber"));
            exchange.getIn().setBody(output);
            JsonObject json = new JsonObject();
            json.put("licenseNumber", output.get("licenseNumber"));
            json.put("type", output.get("type"));
            json.put("startDate", output.get("startDate"));
            exchange.setProperty("license", json);
        }
    }
}
