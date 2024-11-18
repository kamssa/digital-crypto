package com.kamssa.digital_crypto.test;

import com.kamssa.digital_crypto.utilitaire.CryptoUtilImpl;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

public class TestSignature {
    public static void main(String[] args)  throws  Exception{
        CryptoUtilImpl cryptoUtil = new CryptoUtilImpl();
        String secret = "azerty";
        String document = "this is my message";
        String signature = cryptoUtil.hmacSign(document.getBytes(), secret);
        String signedDocument = document + "_.._" + signature;
        System.out.println(signedDocument);
        System.out.println("==============================");
        System.out.println("Verification de la signature");
        String singnedDoc = "this is my message_.._2VkBGVdX20J5uzqPUyKhYCCyapRPChSXtPYOCX4hOIE=";
        String sec = "azerty";
        boolean resultSignature = cryptoUtil.hmacVerify(singnedDoc, sec);
        System.out.println(resultSignature==true? "signature OK": "signature not OK");

        System.out.println("======== Test signature RSA");

        KeyPair  keyPair = cryptoUtil.generateKeyPair();
        PublicKey aPublic = keyPair.getPublic();
        PrivateKey aPrivate = keyPair.getPrivate();

        PrivateKey privateKey = cryptoUtil.privateKeyFromJKS("traore.JKS", "123456789", "traore");
        String data = "mon message que je veux signer";
        String sign = cryptoUtil.rsaSign(data.getBytes(), privateKey);
        String docSigne = data+"_.._"+sign;
        System.out.println("signature avec RSA");
        System.out.println(docSigne);

        System.out.println("======== Verication signature RSA");
        String docReceive ="mon message que je veux signer_.._g5fpr1hz0ThhZdvlahH1cuMQMKYNAHioQajCCUJRMYwbYL+G4SECaghkgV8EvylJAGAehunbhFc/5cXq5zlj6MWoFF5W0yZ6wbDytrEiNqgL3wM1qip/EzHoQSC3CWOmSmPWMnpAqmRX/uPQBQgCN6ynmBZADSYQ5l+6m11tryMTlIJ7QwbFGmqCjdVz2OsB3udNeEf0pTeQEtPmsjdpxO3YRsCrdT0a3B09Ixp9YET5isRi1QQn8cdjwJzkniwGmUF91+xTkg2ds7EBxypng8tVy8ekavxf+EpbhwChvHSPyp3ubCa0zIYKoAE7Oj1SKTKsw5kyF8vKCWzb7dXLMw==";
        PublicKey publicKey = cryptoUtil.publicKeyFromCertificat("myCertificat.cert");
        boolean b = cryptoUtil.rsaSignVerify(docReceive, publicKey);
        System.out.println(b?"signatture  verifier avec RSA ok": "signature not ok");

    }
}

