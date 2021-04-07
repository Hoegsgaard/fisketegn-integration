package dk.fishery.fisketegn.processors;

import com.mongodb.BasicDBObject;
import dk.fishery.fisketegn.model.License;
import dk.fishery.fisketegn.model.User;
import org.apache.camel.Exchange;
import org.bson.Document;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;

public class updateLicenseProcessor implements org.apache.camel.Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
        LinkedHashMap<String,String> prop = (LinkedHashMap) exchange.getProperty("licenseID");
        Document input = (Document) exchange.getProperty("user");
        String licenseID = prop.get("licenseID");
        ArrayList<String> listOfLicenses = (ArrayList<String>) input.get("licenses");
        if(listOfLicenses.contains(licenseID)){
            //License belongs to user
            //TODO: check if license is expired.
            ArrayList body = (ArrayList) exchange.getIn().getBody();
            Document doc = (Document) body.get(0);
            BasicDBObject output = new BasicDBObject(doc);
            /*License license = new License();
            license.setLicenseNumber(doc.getString("licenseNumber"));
            license.setLicenseID(doc.getString("licenseID"));
            license.setType(doc.getString("type"));                    System.out.println("asdfadsf");

            license.setOriginalStartDate(doc.getString("originalStartDate"));
            license.setHighQuality(doc.getBoolean("highQuality"));
            license.setStatus(doc.getBoolean("status"));
            license.setDeletedFlag(doc.getBoolean("deletedFlag"));
            license.setGroupLicenseFlag(doc.getBoolean("groupLicenseFlag"));
            license.setStartDate(date);*/
            String date = new SimpleDateFormat("dd/MM/yyyy").format(new Date());
            output.put("startDate", date);

            exchange.getIn().setBody(output);
        }
        // User user = (User) exchange.getIn().getBody();
        System.out.println("test");
    }
}
