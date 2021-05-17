package dk.fishery.fisketegn.processors;

import com.mongodb.BasicDBObject;
import dk.fishery.fisketegn.model.License;
import dk.fishery.fisketegn.model.User;
import org.apache.camel.Exchange;
import org.apache.camel.util.json.JsonObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class CreateLicenseProcessor implements org.apache.camel.Processor {
    private static int licesenceNumberCounter;
    @Override
    public void process(Exchange exchange) throws Exception {
        User input =(User) exchange.getProperty("oldBody");
        licesenceNumberCounter =   (int) exchange.getProperty("licenseNumber");
        License license = new License();
        license.setLicenseID(UUID.randomUUID().toString());
        license.setLicenseNumber(String.valueOf(licesenceNumberCounter));
        license.setType(input.getType());
        license.setStatus(true);
        license.setStartDate(input.getStartDate());
        license.setEndDate(getEndDate(license.getStartDate(),license.getType()));
        license.setOriginalStartDate(input.getStartDate());
        license.setHighQuality(input.isHighQuality());
        exchange.setProperty("licenseID", license.getLicenseID());
        exchange.setProperty("licenseNumber", license.getLicenseNumber());
        exchange.getIn().setBody(license);
        JsonObject json = new JsonObject();
        json.put("licenseNumber", license.getLicenseNumber());
        json.put("type", license.getType());
        json.put("startDate", license.getStartDate());
        json.put("endDate", license.getEndDate());
        exchange.setProperty("license",json);
        licesenceNumberCounter++;
    }

        private String getEndDate(String oldStartDate, String type){
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate oldDate = LocalDate.parse(oldStartDate, dtf);
        LocalDate today = LocalDate.now();
        LocalDate endDate = LocalDate.now();
        if(type.equals("d")){
            endDate = oldDate.plusDays(1);
        }else if(type.equals("w")){
            endDate = oldDate.plusDays(7);
        }else if(type.equals("y")){
            endDate = oldDate.plusDays(365);
        }

        return endDate.format(dtf);
    }
}
