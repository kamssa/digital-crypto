package com.kamssa.digital_crypto.test;

import javax.crypto.Cipher;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class TestRSADecrypt {
    public static void main(String[] args)  throws  Exception{


      //  private key
    //    MIICdQIBADANBgkqhkiG9w0BAQEFAASCAl8wggJbAgEAAoGBAJa9Fvf42X3YWHJweEkGJnE75NZJFXo4rZbpKEyqqy0CgzAaa9A2HXx1OPWjE+efi743H04/8eObZMJyNRZ3eyzNr7sA9XP87Clz6UhAGMaHjgC7Ry3XrMvZ5/MvWG6o7X7/d5dE5IGlAnOS/jBB6DcvIOY4Ii3HkQZjIeHNxkK9AgMBAAECgYAESaWlG7rSsGbiU8kAHsDU2LogzvG288oa8E/j+mN7GMay2sA1Vcy56mWv4FnObVnevmz8Cf1ElvojPndnHQfvtPlyaqnMndq/IvoaR5JylhgeckNmtE3+mvmF24puMoSv+F6sDNKkwphpXq1WeubRssdqKRoizKb9MuqjIteScQJBAMFL+mMfilvMDYruzwFZ0UYE45VO0gmFgKs9qrQf67+Lkq3k4z2Pdzc+Pk8e/rbxWd8vqNBjJe/PwIeRyiBcthUCQQDHovAMPVO3Uo4RC4M+92xhmJpzyWW115xYvPuL9Tv9Sa+/6yE6HdLwVzLCF6i1p5z77bkz+qTb+PTZC3kup2wJAkAapqsjN5oQBhZn7X1FJmkgSlRGpdN31Jxk/9+lbjFG+6uBpmled4VsbHyS1Ccyehx2FVAlS0ZTxkU5a/R+ecnxAkBKNSX31sAMr/JBIb9qo3w1Fw2qpp1ZJ8llLvJuRv1CnKZot5VFThq/3hnvDe5Xf/OZrfce/DdV0UfqehpUnkVJAkBb2bOQEusUhvrx4O3E4xPIxqzH94MaFbUOTEAHoW75ND8QOqMqw4OckgDwNMZ0dhShsLSkepz3JBf4c2wez4BQ
    //    pblic key
    //    MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCWvRb3+Nl92FhycHhJBiZxO+TWSRV6OK2W6ShMqqstAoMwGmvQNh18dTj1oxPnn4u+Nx9OP/Hjm2TCcjUWd3ssza+7APVz/Owpc+lIQBjGh44Au0ct16zL2efzL1huqO1+/3eXROSBpQJzkv4wQeg3LyDmOCItx5EGYyHhzcZCvQIDAQAB

        String privateKeyBase64 ="MIICdQIBADANBgkqhkiG9w0BAQEFAASCAl8wggJbAgEAAoGBAJa9Fvf42X3YWHJweEkGJnE75NZJFXo4rZbpKEyqqy0CgzAaa9A2HXx1OPWjE+efi743H04/8eObZMJyNRZ3eyzNr7sA9XP87Clz6UhAGMaHjgC7Ry3XrMvZ5/MvWG6o7X7/d5dE5IGlAnOS/jBB6DcvIOY4Ii3HkQZjIeHNxkK9AgMBAAECgYAESaWlG7rSsGbiU8kAHsDU2LogzvG288oa8E/j+mN7GMay2sA1Vcy56mWv4FnObVnevmz8Cf1ElvojPndnHQfvtPlyaqnMndq/IvoaR5JylhgeckNmtE3+mvmF24puMoSv+F6sDNKkwphpXq1WeubRssdqKRoizKb9MuqjIteScQJBAMFL+mMfilvMDYruzwFZ0UYE45VO0gmFgKs9qrQf67+Lkq3k4z2Pdzc+Pk8e/rbxWd8vqNBjJe/PwIeRyiBcthUCQQDHovAMPVO3Uo4RC4M+92xhmJpzyWW115xYvPuL9Tv9Sa+/6yE6HdLwVzLCF6i1p5z77bkz+qTb+PTZC3kup2wJAkAapqsjN5oQBhZn7X1FJmkgSlRGpdN31Jxk/9+lbjFG+6uBpmled4VsbHyS1Ccyehx2FVAlS0ZTxkU5a/R+ecnxAkBKNSX31sAMr/JBIb9qo3w1Fw2qpp1ZJ8llLvJuRv1CnKZot5VFThq/3hnvDe5Xf/OZrfce/DdV0UfqehpUnkVJAkBb2bOQEusUhvrx4O3E4xPIxqzH94MaFbUOTEAHoW75ND8QOqMqw4OckgDwNMZ0dhShsLSkepz3JBf4c2wez4BQ";
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        byte[] decodeKey = Base64.getDecoder().decode(privateKeyBase64);
        PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decodeKey));


        ///////////////////////////////////////////////////////////////////////

        // permet de decrypter une donée avec une clé privée /////////////
        ///////////////////////////////////////////
        String encryptedData = "PtU7GsKeftOS/LOBCh11MuIiy/n7s/0q9ZAqi7n2Slp56z0SkBGZJ/pTVpKgd6jHInqaRoTQ8NKJX4QgisrO6NpUI7PzqncOfFwnzbSK8C3DCCjlE1ZQ3myttg31YuHuR0CXQNhDBNZ3zUhuxZU4qkNadG4HsI5D00TcdR3IvOw=";
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] deryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
        System.out.println("Message decrypté");
         System.out.println( new String( deryptedBytes));

    }
}
