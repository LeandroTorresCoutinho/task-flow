package com.auth.authservice.auth;

import com.nimbusds.jose.jwk.JWKSet;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class JwkSetController {

    private final JWKSet publicJwkSet;

    JwkSetController(JWKSet publicJwkSet) {
        this.publicJwkSet = publicJwkSet;
    }

    @GetMapping("/.well-known/jwks.json")
    Map<String, Object> keys() {
        return publicJwkSet.toJSONObject();
    }
}
