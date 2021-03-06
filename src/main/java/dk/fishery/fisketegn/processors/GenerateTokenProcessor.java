package dk.fishery.fisketegn.processors;

import com.mongodb.BasicDBObject;
import io.jsonwebtoken.SignatureAlgorithm;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import io.jsonwebtoken.*;
import org.apache.camel.util.json.JsonObject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class GenerateTokenProcessor implements Processor {

  @Override
  public void process(Exchange exchange) throws Exception {
    BasicDBObject user = exchange.getIn().getBody(BasicDBObject.class);
    String tokenKey = (String) exchange.getProperty("tokenKey");
    String token = Jwts.builder()
      .claim("id", user.getString("email"))
      .claim("role", user.getString("role"))
      .setIssuedAt(Date.from(Instant.now()))
      .setExpiration(Date.from(Instant.now().plus(2, ChronoUnit.HOURS)))
      .signWith(SignatureAlgorithm.HS512, tokenKey)
      .compact();
    exchange.setProperty("userToken", token);

    JsonObject body = new JsonObject();
    body.put("token", token);

    exchange.getIn().setBody(body);
  }
}
