package dk.fishery.fisketegn.processors;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.bson.Document;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class GetExpiredLicensesProcessor implements Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
        ArrayList<String> expiredLicenses = new ArrayList<>();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        ZoneId zid = ZoneId.of("Europe/Paris");
        for(Document license: (ArrayList<Document>) exchange.getIn().getBody()){
            LocalDate endDate = LocalDate.parse(license.getString("endDate"),dtf);
            if(endDate.isBefore(LocalDate.now())){
                expiredLicenses.add(license.getString("licenseID"));
            }

        }
        if(!expiredLicenses.isEmpty()){
            exchange.setProperty("LicensesToDisable", true);
        }else {
            exchange.setProperty("LicensesToDisable", false);
        }
        exchange.setProperty("licenseIDs", expiredLicenses);
    }
}
