package dk.fishery.fisketegn.processors;

import com.mongodb.BasicDBObject;
import dk.fishery.fisketegn.model.User;
import io.jsonwebtoken.SignatureAlgorithm;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class generateToken implements Processor {

  @Value("${jwtSecure.key}")
  String key;

  @Override
  public void process(Exchange exchange) throws Exception {
    BasicDBObject user = exchange.getIn().getBody(BasicDBObject.class);
    String tokenKey = (String) exchange.getProperty("tokenKey");
    String token = Jwts.builder()
      .claim("id", user.getString("email"))
      .claim("role", user.getString("role"))
      .setIssuedAt(Date.from(Instant.now()))
      .setExpiration(Date.from(Instant.now().plus(1, ChronoUnit.MINUTES)))
      .signWith(SignatureAlgorithm.HS512, tokenKey)
      .compact();
    exchange.setProperty("userToken", token);
    exchange.getIn().setBody(token);
  }
}
