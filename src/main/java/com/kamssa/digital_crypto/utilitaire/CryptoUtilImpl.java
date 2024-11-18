package com.kamssa.digital_crypto.utilitaire;

import org.apache.commons.codec.binary.Hex;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.io.FileInputStream;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Formatter;

public class CryptoUtilImpl {

    public  String encocodeToBase64(byte [] data){
        return Base64.getEncoder().encodeToString(data);
    }
    public  byte[] encodeFromBase64(String dataBase64){
        return Base64.getDecoder().decode(dataBase64);
    }
    public  String encodeToBase64Url(byte [] data){
        return Base64.getUrlEncoder().encodeToString(data);
    }
    public  byte[] encodeFromBase64Url(String data64){
        return Base64.getUrlDecoder().decode(data64);
    }
    public String encodeToHex(byte[] data){
        return DatatypeConverter.printHexBinary(data);
    }
    public String encodeToHexApachCodex(byte[] data){
        return Hex.encodeHexString(data);
    }
    public String encodeToHexNative(byte[] data){
        Formatter formatter = new Formatter();
        for (byte b: data){
            formatter.format("%02x", b);
        }
        return  formatter.toString();
    }
 public  SecretKey genrateSecretKey() throws  Exception{
     KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
     keyGenerator.init(256);
     return keyGenerator.generateKey();
 }
    public  SecretKey genrateSecretKey(String secret) throws  Exception{
       SecretKey secretKey = new SecretKeySpec(secret.getBytes(), 0, secret.length(), "AES");
       return  secretKey;
    }
    public  String encrypteAES(byte[] data, SecretKey secretKey) throws  Exception{
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encryptedData = cipher.doFinal(data);
        String encodeEncryptedData = Base64.getEncoder().encodeToString(encryptedData);
        return encodeEncryptedData;
    }
    public  byte[] decrypteAES(String encodedEncryptData, SecretKey secretKey) throws  Exception{
        byte[] decodeEncryptedData = Base64.getDecoder().decode(encodedEncryptData);
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decodeEncryptedByte = cipher.doFinal(decodeEncryptedData);
        return decodeEncryptedByte;
    }
    // generer un keypair qui contient deux cle d'ou une cle privée et une cle public
    public KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);
        return  keyPairGenerator.generateKeyPair();
    }
    public PublicKey publicKeyFromBase64(String base64PublicKey) throws Exception{
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        byte[] decodePK = Base64.getDecoder().decode(base64PublicKey);
        PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(decodePK));
        return publicKey;

    }
    public PrivateKey privateKeyFromBase64(String base64PrivteKey) throws Exception{
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        byte[] decodePK = Base64.getDecoder().decode(base64PrivteKey);
        PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decodePK));
        return privateKey;

    }
    public String encryptRSA(byte[] data, PublicKey publicKey) throws  Exception{
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedData = cipher.doFinal(data);
        return encocodeToBase64(encryptedData);

    }
    public byte[] decryptRSA(String data, PrivateKey privateKey) throws  Exception{
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decodeEncryptedDatabytes = encodeFromBase64(data);
        byte[] decryptedData = cipher.doFinal(decodeEncryptedDatabytes);
        return decryptedData;

    }
    public  PublicKey publicKeyFromCertificat(String fileName) throws  Exception{
        FileInputStream fis = new FileInputStream(fileName);
        CertificateFactory certificateFactory  = CertificateFactory.getInstance("X.509");
        Certificate certificate = certificateFactory.generateCertificate(fis);
        //System.out.println(certificate.toString());
        return  certificate.getPublicKey();

    }
    public  PrivateKey privateKeyFromJKS(String fileName, String jksPasswor, String alias) throws  Exception{
        FileInputStream fis = new FileInputStream(fileName);
        KeyStore instance = KeyStore.getInstance(KeyStore.getDefaultType());
        instance.load(fis, jksPasswor.toCharArray());
        Key key = instance.getKey(alias, jksPasswor.toCharArray());
        PrivateKey privateKey = (PrivateKey) key;
        return  privateKey;


    }
    // methode qui permet de generer la signature d'un document avec HMAC
    public String hmacSign(byte [] data, String privatesecret) throws  Exception{
        SecretKeySpec secretKeySpec = new SecretKeySpec(privatesecret.getBytes(), "HmacSHA256");
        Mac mac  = Mac.getInstance("HmacSHA256");
        mac.init(secretKeySpec);
        byte[] signature = mac.doFinal(data);
        return Base64.getEncoder().encodeToString(signature);

    }
 // verifier la signature du document
    public boolean hmacVerify(String signedDocument, String secret) throws  Exception{
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        Mac mac  = Mac.getInstance("HmacSHA256");
        String[] splitedDocument = signedDocument.split("_.._");
        String document = splitedDocument[0];
        String documentSignature = splitedDocument[1];
        mac.init(secretKeySpec);
        byte[] sign = mac.doFinal(document.getBytes());
        String base64Sign = encocodeToBase64(sign);
        return  (base64Sign.equals(documentSignature));
    }
    public String rsaSign(byte[]data, PrivateKey privateKey) throws  Exception{

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey, new SecureRandom());
        signature.update(data);
        byte[] sign = signature.sign();
        return encocodeToBase64(sign);
    }
   public boolean rsaSignVerify( String documentSigne, PublicKey publicKey) throws  Exception{

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        String [] data = documentSigne.split("_.._");
        String document = data[0];
        String sign = data[1];
       byte[] decodeSign = Base64.getDecoder().decode(sign);
       signature.update(document.getBytes());
       boolean verify = signature.verify(decodeSign);
       return verify;
    }
}
