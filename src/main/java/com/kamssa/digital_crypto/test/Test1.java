package com.kamssa.digital_crypto.test;

import com.kamssa.digital_crypto.utilitaire.CryptoUtilImpl;

import javax.xml.bind.DatatypeConverter;
import java.util.Arrays;


public class Test1 {
    public static void main(String[] args) {
        CryptoUtilImpl  cryptoUtil = new CryptoUtilImpl();
        String document = "This is my message>>> ";
        String dataBase64 = cryptoUtil.encocodeToBase64(document.getBytes());
        System.out.println(dataBase64);
        String dataBase64Url = cryptoUtil.encodeToBase64Url(document.getBytes());
        System.out.println(dataBase64Url);

        byte[] decodedbytesBase64 = cryptoUtil.encodeFromBase64(dataBase64);
        System.out.println(new String(decodedbytesBase64));

        byte[] decodedbytesBase64Url = cryptoUtil.encodeFromBase64Url(dataBase64Url);
        System.out.println(new String(decodedbytesBase64Url));

        byte[] dataBytes = dataBase64.getBytes();
        System.out.println(Arrays.toString(dataBytes));
        String dataHex = DatatypeConverter.printHexBinary(dataBytes);
        System.out.println(dataHex);
        byte[] bytes = DatatypeConverter.parseHexBinary(dataHex);
        System.out.println(new String(document.getBytes()));

        String s = cryptoUtil.encodeToHex(document.getBytes());
        String s1 = cryptoUtil.encodeToHexApachCodex(document.getBytes());
        String s2 = cryptoUtil.encodeToHexNative(document.getBytes());
        System.out.println(s);
        System.out.println(s1);
        System.out.println(s2);

    }
}
