/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.keybox;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

/** No logging, persistence or networking: imported signing material stays in memory. */
final class Keybox {
    static final int MAX_BYTES = 256 * 1024;
    final List<X509Certificate> certificates = new ArrayList<>();
    final List<List<X509Certificate>> chains = new ArrayList<>();
    final boolean aosp;
    boolean expired;
    private Keybox(boolean aosp) { this.aosp = aosp; }

    static byte[] read(InputStream input, int limit) throws IOException {
        if (input == null) throw new IOException("Input unavailable");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count > limit - out.size()) throw new IOException("Input too large");
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    static byte[] pem(String text) {
        return Base64.getMimeDecoder().decode(text.replaceAll("-----[^-]+-----", "").trim());
    }
    static X509Certificate certificate(String text) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
            .generateCertificate(new ByteArrayInputStream(pem(text)));
    }
    private static boolean trusted(PublicKey key, boolean aosp) throws Exception {
        List<byte[]> roots = new ArrayList<>();
        if (aosp) {
            roots.add(Base64.getDecoder().decode(Roots.AOSP_ROOT_EC));
            roots.add(Base64.getDecoder().decode(Roots.AOSP_ROOT_RSA));
        } else {
            roots.add(Base64.getDecoder().decode(Roots.GOOGLE_ROOT_RSA));
            roots.add(certificate(Roots.GOOGLE_ROOT_EC_CERT).getPublicKey().getEncoded());
        }
        for (byte[] root : roots) if (Arrays.equals(key.getEncoded(), root)) return true;
        return false;
    }
    private static Element one(Element parent, String name) {
        NodeList nodes = parent.getElementsByTagName(name);
        if (nodes.getLength() != 1) throw new IllegalArgumentException("Invalid keybox structure");
        return (Element) nodes.item(0);
    }

    static Keybox parse(String xml, boolean allowAosp) throws Exception {
        if (xml == null || xml.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Invalid keybox size");
        // Reject entity declarations before any parser work, including on Android parsers
        // which do not implement every JAXP hardening feature.
        if (xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY"))
            throw new IllegalArgumentException("XML declarations are not supported");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setExpandEntityReferences(false);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        Element root = document.getDocumentElement();
        if (!root.getTagName().equals("AndroidAttestation")) throw new IllegalArgumentException("Invalid keybox root");
        if (root.getElementsByTagName("Keybox").getLength() != 1 ||
            !one(root, "NumberOfKeyboxes").getTextContent().trim().equals("1"))
            throw new IllegalArgumentException("Select a file containing one keybox");
        Keybox result = new Keybox(allowAosp);
        Element box = one(root, "Keybox");
        NodeList keys = box.getElementsByTagName("Key");
        if (keys.getLength() < 1 || keys.getLength() > 2) throw new IllegalArgumentException("Invalid key count");
        Set<String> algorithms = new HashSet<>();
        for (int i = 0; i < keys.getLength(); i++) {
            Element key = (Element) keys.item(i);
            String algorithm = key.getAttribute("algorithm").toLowerCase(Locale.ROOT);
            if (!(algorithm.equals("ecdsa") || algorithm.equals("rsa")) || !algorithms.add(algorithm))
                throw new IllegalArgumentException("Unsupported or repeated key algorithm");
            Element chain = one(key, "CertificateChain");
            NodeList certs = chain.getElementsByTagName("Certificate");
            if (certs.getLength() < 2 || certs.getLength() > 8 ||
                Integer.parseInt(one(chain, "NumberOfCertificates").getTextContent().trim()) != certs.getLength())
                throw new IllegalArgumentException("Invalid certificate chain length");
            List<X509Certificate> parsed = new ArrayList<>();
            for (int n = 0; n < certs.getLength(); n++) {
                X509Certificate cert = certificate(certs.item(n).getTextContent());
                try { cert.checkValidity(); }
                catch (CertificateExpiredException | CertificateNotYetValidException e) { result.expired = true; }
                parsed.add(cert);
            }
            for (int n = 0; n + 1 < parsed.size(); n++) {
                if (!parsed.get(n).getIssuerX500Principal().equals(parsed.get(n+1).getSubjectX500Principal()))
                    throw new IllegalArgumentException("Certificate issuer mismatch");
                parsed.get(n).verify(parsed.get(n+1).getPublicKey());
            }
            X509Certificate top = parsed.get(parsed.size()-1);
            if (!trusted(top.getPublicKey(), allowAosp))
                throw new IllegalArgumentException(allowAosp ? "Unexpected AOSP root" : "Keybox is not signed by Google");
            top.verify(top.getPublicKey());
            PrivateKey secret = privateKey(one(key, "PrivateKey").getTextContent(), parsed.get(0).getPublicKey(), algorithm);
            byte[] challenge = new byte[32]; new SecureRandom().nextBytes(challenge);
            Signature signer = Signature.getInstance(algorithm.equals("rsa") ? "SHA256withRSA" : "SHA256withECDSA");
            signer.initSign(secret); signer.update(challenge); byte[] signed = signer.sign();
            signer.initVerify(parsed.get(0).getPublicKey()); signer.update(challenge);
            if (!signer.verify(signed)) throw new IllegalArgumentException("Private key does not match its certificate");
            result.certificates.addAll(parsed);
            result.chains.add(parsed);
        }
        return result;
    }

    private static PrivateKey privateKey(String text, PublicKey publicKey, String algorithm) throws Exception {
        String jca = algorithm.equals("rsa") ? "RSA" : "EC";
        if (!publicKey.getAlgorithm().equals(jca)) throw new IllegalArgumentException("Key algorithm mismatch");
        byte[] encoded = pem(text);
        if (text.contains("BEGIN PRIVATE KEY"))
            return KeyFactory.getInstance(jca).generatePrivate(new PKCS8EncodedKeySpec(encoded));
        Der sequence = new Der(encoded).sequence();
        sequence.value(2); // version
        if (text.contains("BEGIN RSA PRIVATE KEY") && jca.equals("RSA")) {
            BigInteger[] v = new BigInteger[8];
            for (int i = 0; i < v.length; i++) v[i] = new BigInteger(sequence.value(2));
            return KeyFactory.getInstance(jca).generatePrivate(new RSAPrivateCrtKeySpec(v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7]));
        }
        if (text.contains("BEGIN EC PRIVATE KEY") && publicKey instanceof ECPublicKey)
            return KeyFactory.getInstance(jca).generatePrivate(new ECPrivateKeySpec(new BigInteger(1,sequence.value(4)),((ECPublicKey)publicKey).getParams()));
        throw new IllegalArgumentException("Unsupported private key format");
    }
    private static final class Der {
        final byte[] bytes; int position;
        Der(byte[] bytes) { this.bytes = bytes; }
        Der sequence() { return new Der(value(0x30)); }
        byte[] value(int tag) {
            if (position+2 > bytes.length || (bytes[position++]&255) != tag) throw new IllegalArgumentException("Invalid private key encoding");
            int length = bytes[position++]&255;
            if (length > 127) {
                int count = length&127; length=0;
                if (count == 0 || count > 3 || position+count > bytes.length) throw new IllegalArgumentException("Invalid DER length");
                for (int i=0;i<count;i++) length=(length<<8)|(bytes[position++]&255);
            }
            if (length > bytes.length-position) throw new IllegalArgumentException("Truncated private key");
            byte[] result=Arrays.copyOfRange(bytes,position,position+length); position+=length; return result;
        }
    }
    boolean revoked(Set<String> entries) {
        for (X509Certificate cert : certificates)
            if (entries.contains(cert.getSerialNumber().toString(16).toLowerCase(Locale.ROOT))) return true;
        return false;
    }
}
