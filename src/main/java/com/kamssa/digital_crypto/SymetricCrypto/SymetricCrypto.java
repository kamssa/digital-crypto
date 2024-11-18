package com.kamssa.digital_crypto.SymetricCrypto;

import com.kamssa.digital_crypto.utilitaire.CryptoUtilImpl;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.Base64;


public class SymetricCrypto {
    public static void main(String[] args) throws Exception{

        CryptoUtilImpl cryptoUtil = new CryptoUtilImpl();
        SecretKey secretKey = cryptoUtil.genrateSecretKey();
        SecretKey secretKey2= cryptoUtil.genrateSecretKey("azerty_azerty_az");
        byte[] secreKeyBytes = secretKey.getEncoded();
        System.out.println(Arrays.toString(secreKeyBytes));
        String encodedSecretKey = Base64.getEncoder().encodeToString(secreKeyBytes);
        System.out.println(encodedSecretKey);
        System.out.println("=======================================");
        String data = "Hello word";
        String encryptData = cryptoUtil.encrypteAES(data.getBytes(), secretKey2);
        System.out.println(encryptData);
        byte[] decryptBytes = cryptoUtil.decrypteAES(encryptData, secretKey2);
        System.out.println(new String(decryptBytes));


    }
}
