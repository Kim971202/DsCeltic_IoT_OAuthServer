package com.oauth.jwt;

import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

@Component
public class KeyStoreUtils {

    public String resourceFileToString(String path) throws Exception {

        System.out.println("KeyStoreUtils -> resourceFileToString(String path)");
        System.out.println("Path: " + path);
        return new String(FileCopyUtils.copyToByteArray(new ClassPathResource(path).getInputStream()), "UTF-8");
    }

    /**
     * AES 대칭키 생성
     *
     * @return
     * @throws Exception
     */
    public String createAesEncryptKeyBase64() throws Exception {

        System.out.println("KeyStoreUtils -> createAesEncryptKeyBase64()");

        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);

        return Base64.getEncoder().encodeToString(keyGen.generateKey().getEncoded());
    }

    /**
     * AES 대칭키 생성
     *
     * @return
     * @throws Exception
     */
    public SecretKey createAesEncryptKey() throws Exception {

        System.out.println("KeyStoreUtils -> createAesEncryptKey()");

        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);

        return keyGen.generateKey();
    }

    /**
     * AES 대칭키 반환
     *
     * @param path
     * @return
     * @throws Exception
     */
    public SecretKey getAesEncryptKey(String path) throws Exception {

        System.out.println("KeyStoreUtils -> getAesEncryptKey(String path)");
        System.out.println("path: " + path);
        String key = resourceFileToString(path);

        byte[] decodedKey = Base64.getDecoder().decode(key);
        SecretKey aesKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");

        return aesKey;
    }

    /**
     * RAS public/private key 생성
     *
     * @param keyId
     * @return
     * @throws Exception
     */
    public KeyPair createRsaAesEncryptKey() throws Exception {

        System.out.println("KeyStoreUtils -> createRsaAesEncryptKey()");

        RSAKey rsaKey = new RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .generate();

        return new KeyPair(rsaKey.toPublicKey(), rsaKey.toPrivateKey());

    }

    /**
     * RSA public key 반환
     *
     * @param base64Str
     * @return
     * @throws Exception
     */
    public RSAPublicKey getPublicKey(String base64Str) throws Exception {

        System.out.println("KeyStoreUtils -> getPublicKey(String base64Str)");

        byte[] decoded = Base64.getDecoder().decode(base64Str);

        System.out.println("decoded: " + Arrays.toString(decoded));

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    }

    /**
     * RSA private key 반환
     *
     * @param base64Str
     * @return
     * @throws Exception
     */
    public RSAPrivateKey getPrivateKey(String base64Str) throws Exception {

        System.out.println("KeyStoreUtils -> getPrivateKey(String base64Str)");

        byte[] decoded = Base64.getDecoder().decode(base64Str);

        System.out.println("decoded: " + Arrays.toString(decoded));

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
    }

    /**
     * RSA public key 반환
     *
     * @param path
     * @return
     * @throws Exception
     */
    public RSAPublicKey readPublicKey(String path) throws Exception {

        System.out.println("KeyStoreUtils -> readPublicKey(String path) :" + path);
        System.out.println("path: " + path);

        String publicKeyPEM = resourceFileToString(path);

        byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
        return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    }

    /**
     * RSA private key 반환
     *
     * @param path
     * @return
     * @throws Exception
     */
    public RSAPrivateKey readPrivateKey(String path) throws Exception {

        System.out.println("KeyStoreUtils -> readPrivateKey(String path)");
        System.out.println("path: " + path);

        String privateKeyPEM = resourceFileToString(path);
        System.out.println("privateKeyPEM: " + privateKeyPEM);

        byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
    }
}
