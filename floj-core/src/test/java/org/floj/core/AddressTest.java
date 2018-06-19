/*
 * Copyright 2011 Google Inc.
 * Copyright 2014 Andreas Schildbach
 * Copyright 2018 DeSoto Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.floj.core;

import org.floj.core.Address;
import org.floj.core.AddressFormatException;
import org.floj.core.DumpedPrivateKey;
import org.floj.core.ECKey;
import org.floj.core.NetworkParameters;
import org.floj.core.Utils;
import org.floj.core.WrongNetworkException;
import org.floj.params.MainNetParams;
import org.floj.params.Networks;
import org.floj.params.TestNet3Params;
import org.floj.script.Script;
import org.floj.script.ScriptBuilder;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;

import static org.floj.core.Utils.HEX;
import static org.junit.Assert.*;

public class AddressTest {
    static final NetworkParameters testParams = TestNet3Params.get();
    static final NetworkParameters mainParams = MainNetParams.get();

    @Test
    public void testJavaSerialization() throws Exception {
        Address testAddress = Address.fromBase58(testParams, "ofkkGFgK3JBwLqUzTLcuupM4PjPBH56qd4");
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        new ObjectOutputStream(os).writeObject(testAddress);
        Address testAddressCopy = (Address) new ObjectInputStream(new ByteArrayInputStream(os.toByteArray()))
                .readObject();
        assertEquals(testAddress, testAddressCopy);

        Address mainAddress = Address.fromBase58(mainParams, "F9nYnvA27qGFcxwQKXvVMQn7E8SMQsbaHD");
        os = new ByteArrayOutputStream();
        new ObjectOutputStream(os).writeObject(mainAddress);
        Address mainAddressCopy = (Address) new ObjectInputStream(new ByteArrayInputStream(os.toByteArray()))
                .readObject();
        assertEquals(mainAddress, mainAddressCopy);
    }

    @Test
    public void stringification() throws Exception {
        // Test a testnet address.
        Address a = new Address(testParams, HEX.decode("fb0a5f12b8d54102396fcb259cea199ead505bee"));
        assertEquals("ofkkGFgK3JBwLqUzTLcuupM4PjPBH56qd4", a.toString());
        assertFalse(a.isP2SHAddress());

        Address b = new Address(mainParams, HEX.decode("2b646639fbeade213a81afbb193dff43e2f93d32"));
        assertEquals("F9nYnvA27qGFcxwQKXvVMQn7E8SMQsbaHD", b.toString());
        assertFalse(b.isP2SHAddress());
    }
    
    @Test
    public void decoding() throws Exception {
        Address a = Address.fromBase58(testParams, "ofkkGFgK3JBwLqUzTLcuupM4PjPBH56qd4");
        assertEquals("fb0a5f12b8d54102396fcb259cea199ead505bee", Utils.HEX.encode(a.getHash160()));

        Address b = Address.fromBase58(mainParams, "F9nYnvA27qGFcxwQKXvVMQn7E8SMQsbaHD");
        assertEquals("2b646639fbeade213a81afbb193dff43e2f93d32", Utils.HEX.encode(b.getHash160()));
    }
    
    @Test
    public void errorPaths() {
        // Check what happens if we try and decode garbage.
        try {
            Address.fromBase58(testParams, "this is not a valid address!");
            fail();
        } catch (WrongNetworkException e) {
            fail();
        } catch (AddressFormatException e) {
            // Success.
        }

        // Check the empty case.
        try {
            Address.fromBase58(testParams, "");
            fail();
        } catch (WrongNetworkException e) {
            fail();
        } catch (AddressFormatException e) {
            // Success.
        }

        // Check the case of a mismatched network.
        try {
            Address.fromBase58(testParams, "F9nYnvA27qGFcxwQKXvVMQn7E8SMQsbaHD");
            fail();
        } catch (WrongNetworkException e) {
            // Success.
            assertEquals(e.verCode, MainNetParams.get().getAddressHeader());
            assertTrue(Arrays.equals(e.acceptableVersions, TestNet3Params.get().getAcceptableAddressCodes()));
        } catch (AddressFormatException e) {
            fail();
        }
    }

    @Test
    public void getNetwork() throws Exception {
        NetworkParameters params = Address.getParametersFromAddress("F9nYnvA27qGFcxwQKXvVMQn7E8SMQsbaHD");
        assertEquals(MainNetParams.get().getId(), params.getId());
        params = Address.getParametersFromAddress("ofkkGFgK3JBwLqUzTLcuupM4PjPBH56qd4");
        assertEquals(TestNet3Params.get().getId(), params.getId());
    }

    @Test
    public void getAltNetwork() throws Exception {
        // An alternative network
        class AltNetwork extends MainNetParams {
            AltNetwork() {
                super();
                id = "alt.network";
                addressHeader = 48;
                p2shHeader = 5;
                acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
            }
        }
        AltNetwork altNetwork = new AltNetwork();
        // Add new network params
        Networks.register(altNetwork);
        // Check if can parse address
        NetworkParameters params = Address.getParametersFromAddress("LLxSnHLN2CYyzB5eWTR9K9rS9uWtbTQFb6");
        assertEquals(altNetwork.getId(), params.getId());
        // Check if main network works as before
        params = Address.getParametersFromAddress("F9nYnvA27qGFcxwQKXvVMQn7E8SMQsbaHD");
        assertEquals(MainNetParams.get().getId(), params.getId());
        // Unregister network
        Networks.unregister(altNetwork);
        try {
            Address.getParametersFromAddress("LLxSnHLN2CYyzB5eWTR9K9rS9uWtbTQFb6");
            fail();
        } catch (AddressFormatException e) { }
    }
    
    @Test
    public void p2shAddress() throws Exception {
        // Test that we can construct P2SH addresses
        Address mainNetP2SHAddress = Address.fromBase58(MainNetParams.get(), "4Zh75RGJT2aYtgDQbzsrwX56SXxYf6ysU8");
        assertEquals(mainNetP2SHAddress.version, MainNetParams.get().p2shHeader);
        assertTrue(mainNetP2SHAddress.isP2SHAddress());
        Address testNetP2SHAddress = Address.fromBase58(TestNet3Params.get(), "2P1efmCKpM6tzncRJTFqo3iKbcPv7rrGbAh");
        assertEquals(testNetP2SHAddress.version, TestNet3Params.get().p2shHeader);
        assertTrue(testNetP2SHAddress.isP2SHAddress());

        // Test that we can determine what network a P2SH address belongs to
        NetworkParameters mainNetParams = Address.getParametersFromAddress("4Zh75RGJT2aYtgDQbzsrwX56SXxYf6ysU8");
        assertEquals(MainNetParams.get().getId(), mainNetParams.getId());
        NetworkParameters testNetParams = Address.getParametersFromAddress("2P1efmCKpM6tzncRJTFqo3iKbcPv7rrGbAh");
        assertEquals(TestNet3Params.get().getId(), testNetParams.getId());

        // Test that we can convert them from hashes
        byte[] hex = HEX.decode("db3f9f3fd8cb236033756659fd2c5400f8eeee18");
        Address a = Address.fromP2SHHash(mainParams, hex);
        assertEquals("4Zh75RGJT2aYtgDQbzsrwX56SXxYf6ysU8", a.toString());
        Address b = Address.fromP2SHHash(testParams, HEX.decode("d867625a627322622e74d1f8bdb972c96ab86b40"));
        assertEquals("2P1efmCKpM6tzncRJTFqo3iKbcPv7rrGbAh", b.toString());
        Address c = Address.fromP2SHScript(mainParams, ScriptBuilder.createP2SHOutputScript(hex));
        assertEquals("4Zh75RGJT2aYtgDQbzsrwX56SXxYf6ysU8", c.toString());
    }

    @Test
    public void p2shAddressCreationFromKeys() throws Exception {
        ECKey key1 = DumpedPrivateKey.fromBase58(mainParams, "RAXthr2B3F84imyqg2zAJpAufkZSY1wXHANndzKGqKG6vo6ia3Mg").getKey();
        key1 = ECKey.fromPrivate(key1.getPrivKeyBytes());
        ECKey key2 = DumpedPrivateKey.fromBase58(mainParams, "RESBzoj6quXYhMMYvrHRQDQPB44rYf2JtYtagvYLx6EaFR6C6oXr").getKey();
        key2 = ECKey.fromPrivate(key2.getPrivKeyBytes());
        ECKey key3 = DumpedPrivateKey.fromBase58(mainParams, "RFtPSLis6D8UFCHLvoD4YXMrAMpytDMkC7XuUoJaLaJWis7Ccdj3").getKey();
        key3 = ECKey.fromPrivate(key3.getPrivKeyBytes());

        List<ECKey> keys = Arrays.asList(key1, key2, key3);
        Script p2shScript = ScriptBuilder.createP2SHOutputScript(2, keys);
        Address address = Address.fromP2SHScript(mainParams, p2shScript);
        assertEquals("4Zh75RGJT2aYtgDQbzsrwX56SXxYf6ysU8", address.toString());
    }

    @Test
    public void cloning() throws Exception {
        Address a = new Address(testParams, HEX.decode("fda79a24e50ff70ff42f7d89585da5bd19d9e5cc"));
        Address b = a.clone();

        assertEquals(a, b);
        assertNotSame(a, b);
    }

    @Test
    public void roundtripBase58() throws Exception {
        String base58 = "F9nYnvA27qGFcxwQKXvVMQn7E8SMQsbaHD";
        assertEquals(base58, Address.fromBase58(null, base58).toBase58());
    }

    @Test
    public void comparisonCloneEqualTo() throws Exception {
        Address a = Address.fromBase58(mainParams, "F9nYnvA27qGFcxwQKXvVMQn7E8SMQsbaHD");
        Address b = a.clone();

        int result = a.compareTo(b);
        assertEquals(0, result);
    }

    @Test
    public void comparisonEqualTo() throws Exception {
        Address a = Address.fromBase58(mainParams, "F9nYnvA27qGFcxwQKXvVMQn7E8SMQsbaHD");
        Address b = a.clone();

        int result = a.compareTo(b);
        assertEquals(0, result);
    }

    @Test
    public void comparisonLessThan() throws Exception {
        Address a = Address.fromBase58(mainParams, "F8LbSsrHqAwZ1GiDyvgykANZ8obTp9iQ29");
        Address b = Address.fromBase58(mainParams, "F9nYnvA27qGFcxwQKXvVMQn7E8SMQsbaHD");

        int result = a.compareTo(b);
        assertTrue(result < 0);
    }

    @Test
    public void comparisonGreaterThan() throws Exception {
        Address a = Address.fromBase58(mainParams, "F9nYnvA27qGFcxwQKXvVMQn7E8SMQsbaHD");
        Address b = Address.fromBase58(mainParams, "F8LbSsrHqAwZ1GiDyvgykANZ8obTp9iQ29");

        int result = a.compareTo(b);
        assertTrue(result > 0);
    }

    @Test
    public void comparisonBytesVsString() throws Exception {
        // TODO: To properly test this we need a much larger data set
        Address a = Address.fromBase58(mainParams, "F8LbSsrHqAwZ1GiDyvgykANZ8obTp9iQ29");
        Address b = Address.fromBase58(mainParams, "F9nYnvA27qGFcxwQKXvVMQn7E8SMQsbaHD");

        int resultBytes = a.compareTo(b);
        int resultsString = a.toString().compareTo(b.toString());
        assertTrue( resultBytes < 0 );
        assertTrue( resultsString < 0 );
    }
}
