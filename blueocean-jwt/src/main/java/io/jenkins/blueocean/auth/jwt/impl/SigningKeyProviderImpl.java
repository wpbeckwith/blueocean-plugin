package io.jenkins.blueocean.auth.jwt.impl;

import hudson.Extension;
import io.jenkins.blueocean.auth.jwt.JwtSigningKeyProvider;
import io.jenkins.blueocean.auth.jwt.JwtToken;
import io.jenkins.blueocean.auth.jwt.SigningKey;
import io.jenkins.blueocean.auth.jwt.SigningPublicKey;
import io.jenkins.blueocean.commons.ServiceException;

import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static java.util.logging.Level.*;

/**
 * Default {@link JwtSigningKeyProvider} that rotates a key over time.
 *
 * @author Kohsuke Kawaguchi
 * @author Vivek Pandey
 */
@Extension(ordinal = -9999)
public class SigningKeyProviderImpl extends JwtSigningKeyProvider {
    private static final Logger LOGGER = Logger.getLogger(SigningKeyProviderImpl.class.getName());
    private static final Pattern YYYYMM = Pattern.compile("[0-9]{6}");

    private final AtomicReference<JwtRsaDigitalSignatureKey> key = new AtomicReference<>();

    @Override
    public SigningKey select(JwtToken token) {
        String id = new SimpleDateFormat("yyyyMM").format(new Date());
        JwtRsaDigitalSignatureKey k = key.get();
        if (k==null || !k.getId().equals(id))
            key.set(k=new JwtRsaDigitalSignatureKey(id));
        return k.toSigningKey();
    }

    @Override
    public SigningPublicKey getPublicKey(String kid) {
        if (!YYYYMM.matcher(kid).matches())
            return null;        // not our ID. This also protects against the directory traversal attack in key ID

        JwtRsaDigitalSignatureKey key = new JwtRsaDigitalSignatureKey(kid);
        try {
            if (!key.exists()) {
                return null;
            }
        } catch (IOException e) {
            LOGGER.log(WARNING, String.format("Error reading RSA key for id %s: %s", kid, e.getMessage()), e);
            throw new ServiceException.UnexpectedErrorException("Unexpected error: " + e.getMessage(), e);
        }
        return new SigningPublicKey(kid,key.getPublicKey());
    }
}
