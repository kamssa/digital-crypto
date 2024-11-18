package com.kamssa.digital_crypto.test;

import com.kamssa.digital_crypto.utilitaire.CryptoUtilImpl;

import java.security.PrivateKey;
import java.security.PublicKey;

public class TestRSAJKS {
    public static void main(String[] args) throws  Exception{
        CryptoUtilImpl cryptoUtil = new CryptoUtilImpl();
        PublicKey publicKey = cryptoUtil.publicKeyFromCertificat("myCertificat.cert");
        System.out.println(cryptoUtil.encocodeToBase64(publicKey.getEncoded()));
        PrivateKey privateKey = cryptoUtil.privateKeyFromJKS("traore.JKS","123456789", "traore" );
        System.out.println(cryptoUtil.encocodeToBase64(privateKey.getEncoded()));

        String data = "My secret message";
        String encryptedData = cryptoUtil.encryptRSA(data.getBytes(), publicKey);
        System.out.println("Message crypté");
        System.out.println(encryptedData);
        byte[] decryptedBytes = cryptoUtil.decryptRSA(encryptedData, privateKey);
        System.out.println("Message decrypté");
        System.out.println(new String( decryptedBytes));

    }
    // = > HMAC : H message autentication code (comment generer une signature d'un message) avec le H on peut signer un document
    // RSAwithSHA256 : peut aussi prermettre de signer un document
}
