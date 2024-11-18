package com.kamssa.digital_crypto.test;

import com.kamssa.digital_crypto.utilitaire.CryptoUtilImpl;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class TestRSA {
    public static void main(String[] args)  throws  Exception{


      //  private key
    //    MIICdQIBADANBgkqhkiG9w0BAQEFAASCAl8wggJbAgEAAoGBAJa9Fvf42X3YWHJweEkGJnE75NZJFXo4rZbpKEyqqy0CgzAaa9A2HXx1OPWjE+efi743H04/8eObZMJyNRZ3eyzNr7sA9XP87Clz6UhAGMaHjgC7Ry3XrMvZ5/MvWG6o7X7/d5dE5IGlAnOS/jBB6DcvIOY4Ii3HkQZjIeHNxkK9AgMBAAECgYAESaWlG7rSsGbiU8kAHsDU2LogzvG288oa8E/j+mN7GMay2sA1Vcy56mWv4FnObVnevmz8Cf1ElvojPndnHQfvtPlyaqnMndq/IvoaR5JylhgeckNmtE3+mvmF24puMoSv+F6sDNKkwphpXq1WeubRssdqKRoizKb9MuqjIteScQJBAMFL+mMfilvMDYruzwFZ0UYE45VO0gmFgKs9qrQf67+Lkq3k4z2Pdzc+Pk8e/rbxWd8vqNBjJe/PwIeRyiBcthUCQQDHovAMPVO3Uo4RC4M+92xhmJpzyWW115xYvPuL9Tv9Sa+/6yE6HdLwVzLCF6i1p5z77bkz+qTb+PTZC3kup2wJAkAapqsjN5oQBhZn7X1FJmkgSlRGpdN31Jxk/9+lbjFG+6uBpmled4VsbHyS1Ccyehx2FVAlS0ZTxkU5a/R+ecnxAkBKNSX31sAMr/JBIb9qo3w1Fw2qpp1ZJ8llLvJuRv1CnKZot5VFThq/3hnvDe5Xf/OZrfce/DdV0UfqehpUnkVJAkBb2bOQEusUhvrx4O3E4xPIxqzH94MaFbUOTEAHoW75ND8QOqMqw4OckgDwNMZ0dhShsLSkepz3JBf4c2wez4BQ
    //    pblic key
    //    MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCWvRb3+Nl92FhycHhJBiZxO+TWSRV6OK2W6ShMqqstAoMwGmvQNh18dTj1oxPnn4u+Nx9OP/Hjm2TCcjUWd3ssza+7APVz/Owpc+lIQBjGh44Au0ct16zL2efzL1huqO1+/3eXROSBpQJzkv4wQeg3LyDmOCItx5EGYyHhzcZCvQIDAQAB

        String publucKeyBase64 ="MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCWvRb3+Nl92FhycHhJBiZxO+TWSRV6OK2W6ShMqqstAoMwGmvQNh18dTj1oxPnn4u+Nx9OP/Hjm2TCcjUWd3ssza+7APVz/Owpc+lIQBjGh44Au0ct16zL2efzL1huqO1+/3eXROSBpQJzkv4wQeg3LyDmOCItx5EGYyHhzcZCvQIDAQAB";
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        byte[] decodeKey = Base64.getDecoder().decode(publucKeyBase64);
        PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(decodeKey));


        ///////////////////////////////////////////////////////////////////////

        // permet de crypter une donée avec une clé public /////////////
        ///////////////////////////////////////////
        String data = "un message à chiffrer";
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedBytes = cipher.doFinal(data.getBytes());
        System.out.println("Message crypté");
        System.out.println( Base64.getEncoder().encodeToString(encryptedBytes));
        ///////////////////////////////////////////////////////////////////////////////

        CryptoUtilImpl cryptoUtil = new CryptoUtilImpl();
        KeyPair keyPair = cryptoUtil.generateKeyPair();
        PublicKey aPublic = keyPair.getPublic();
        System.out.println("voir le keypair public");
        System.out.println(aPublic);

        System.out.println("==================================================================");
        String pKpbase64 = cryptoUtil.encocodeToBase64(aPublic.getEncoded());
        System.out.println(pKpbase64);
        PrivateKey aPrivate = keyPair.getPrivate();
        String pKvbase64 = cryptoUtil.encocodeToBase64(aPrivate.getEncoded());
        System.out.println(pKvbase64);

        System.out.println("================================================ generation des clés ======================");
        PublicKey publicKey1 = cryptoUtil.publicKeyFromBase64(publucKeyBase64);
        PrivateKey privateKey1 = cryptoUtil.privateKeyFromBase64(pKvbase64);

        System.out.println("================================================");

        String dataRSA = "Test avec les methode que j'ai créé dans la classe CryptoUtilImpl";
        System.out.println("================================================ Permet de crypter======================");
        String encrypted = cryptoUtil.encryptRSA(dataRSA.getBytes(), aPublic);
        System.out.println("Encrypted");
        System.out.println(encrypted);
        System.out.println("Decrypted");
        byte[] bytes = cryptoUtil.decryptRSA(encrypted, aPrivate);
        System.out.println(new String(bytes));


    }
}
