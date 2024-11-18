package com.kamssa.digital_crypto.AsymetricCrypto;

import com.kamssa.digital_crypto.utilitaire.CryptoUtilImpl;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Base64;

public class GenerateRSAKey {
    public static void main(String[] args) throws Exception {
        CryptoUtilImpl cryptoUtil =  new CryptoUtilImpl();
        KeyPair keyPair = cryptoUtil.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();
        System.out.println("private key");
       // System.out.println(Arrays.toString(privateKey.getEncoded()));
        System.out.println(Base64.getEncoder().encodeToString(privateKey.getEncoded()));
        System.out.println("pblic key");
      //  System.out.println(Arrays.toString(publicKey.getEncoded()));
        System.out.println(Base64.getEncoder().encodeToString(publicKey.getEncoded()));
    }
}
