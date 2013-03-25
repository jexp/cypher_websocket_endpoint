package org.neo4j.remoting.msgpack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.msgpack.MessagePack;
import org.msgpack.annotation.Message;
import org.msgpack.annotation.Optional;
import org.msgpack.packer.BufferPacker;
import org.msgpack.packer.Packer;
import org.msgpack.packer.Unconverter;
import org.msgpack.type.Value;
import org.msgpack.unpacker.BufferUnpacker;
import org.msgpack.unpacker.Converter;
import org.msgpack.unpacker.Unpacker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.List;

public class PackTest {
    @Message
        public static class TestMessage {
            public String name;
            public int age = 100;
            @Optional public byte[] data = new byte[0];

            public TestMessage() { }
        }

    @Test
    public void testPerformance() throws Exception {
        List<?> objects = Arrays.asList("potatoes", false, 11, null);
        MessagePack msgpack = new MessagePack();
        long time = System.currentTimeMillis();
        long counter=0;
        for (int i=0;i<10000;i++) {
            BufferPacker packer = msgpack.createBufferPacker();
            for (int j=0;j<100;j++) {
                packer.write(objects);
            }
            counter+= packer.toByteArray().length;
        }
        System.out.println("took " + (System.currentTimeMillis() - time) + " ms for " + counter + " bytes.");

    }

    @Test
    public void testUnpackClassChange() throws Exception {
        MessagePack msgpack = new MessagePack();
        final File file = new File("TestMessage.msgpack");
        if (file.exists()) {
        final FileInputStream fis = new FileInputStream(file);
            Unpacker unpacker = msgpack.createUnpacker(fis);
            final TestMessage t = unpacker.read(TestMessage.class);
            assertNotNull(t);
            assertEquals(t.name,"bar");
            fis.close();
        }
        final FileOutputStream fos = new FileOutputStream(file);
        Packer packer = msgpack.createPacker(fos);
        final TestMessage t = new TestMessage();
        t.name = "bar";
        packer.write(t);
        fos.close();

    }

    @Test
    public void testPrimitive() throws Exception {
        MessagePack msgpack = new MessagePack();
        BufferPacker packer = msgpack.createBufferPacker();

        TestMessage t = new TestMessage();
        t.name = "foo";
        t.age = 50;
        t.data = new byte[] {42};

        packer.write(t);
        byte[] raw = packer.toByteArray();
        BufferUnpacker unpacker = msgpack.createBufferUnpacker(raw);
        TestMessage u = unpacker.read(TestMessage.class);
        assertEquals(t.name,u.name);
        assertEquals(t.age,u.age);
        assertEquals(t.data.length, u.data.length);
        for (int i = 0; i < t.data.length; i++) {
            assertEquals(t.data[i], u.data[i]);
        }

        Unconverter unconverter = new Unconverter(msgpack);
        unconverter.write(t);
        Value value = unconverter.getResult();
        Converter converter = new Converter(msgpack, value);
        TestMessage v = converter.read(TestMessage.class);
        assertEquals(t.name,v.name);
        assertEquals(t.age,v.age);
        assertEquals(t.data.length, v.data.length);
        for (int i = 0; i < t.data.length; i++) {
            assertEquals(t.data[i], v.data[i]);
        }
    }
}
