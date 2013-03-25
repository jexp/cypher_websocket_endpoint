/*
Copyright (c) 2012, Jonathan Stephens All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.neo4j.remoting.msgpack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



/**
 * Alternate msgpack lib to https://github.com/msgpack/msgpack-java
 * The API is very minimal and simplistic.
 *
 * This implementation uses the builtin Java types:
 * - null
 * - Boolean
 * - Number
 *     byte, short, int, and long are considered interchangeable when
 *     packing/unpacking, BigIntegers will be used for large values in uint64 values
 * - String (UTF-8), byte[], or ByteBuffer (the *whole* buffer) (always unpacked as a byte[] unless you ask for something else)
 * - Map (any type may be used for packing, always unpacked as a HashMap)
 * - List (any type may be used for packing, always unpacked as an ArrayList)
 * Passing any other types will throw an IllegalArumentException.
 *
 * @author jon
 */
public class MsgPack {
	/**
	 * Packs an item using the msgpack protocol.
	 *
	 * Warning: this does not do any recursion checks. If you pass a cyclic object,
	 * you will run in an infinite loop until you run out of memory.
	 *
	 * @param item
	 * @return the packed data
	 * @throws org.neo4j.remoting.msgpack.UnpackableItemException If the given data cannot be packed.
	 */
	public static byte[] pack(Object item) throws UnpackableItemException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			pack(item, new DataOutputStream(out));
		} catch(IOException ex) {
			//this shouldn't happen
			throw new RuntimeException("ByteArrayOutputStream threw an IOException!", ex);
		}
		return out.toByteArray();
	}

	public static final int UNPACK_RAW_AS_STRING = 0x1;
	public static final int UNPACK_RAW_AS_BYTE_BUFFER = 0x2;

	/**
	 * This is the same as calling unpack(data, 0)
	 * @param data
	 * @return
	 * @throws org.neo4j.remoting.msgpack.InvalidMsgPackDataException
	 */
	public static Object unpack(byte[] data) throws InvalidMsgPackDataException {
		return unpack(data, 0);
	}


	/**
	 * Unpacks the given data.
	 *
	 * @param data
	 * @param options
	 * Bitmask of flags to specify how to map certain values back to java types:
	 * For raw types:
	 *    (no option) - All raw bytes are decoded as a byte[]
	 *    OPTION_RAW_AS_STRING - All raw bytes are decoded as a UTF-8 string, with invalid codepoints replaced with a placeholder
	 *    OPTION_RAW_AS_BYTE_BUFFER - All raw bytes are decoded as ByteBuffers (with a backing array)
	 * @return the unpacked data
	 * @throws InvalidMsgPackDataException If the given data cannot be unpacked.
	 */
	public static Object unpack(byte[] data, int options) throws InvalidMsgPackDataException {
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		try {
			return unpack(new DataInputStream(in), options);
		} catch (InvalidMsgPackDataException ex) {
			//InvalidMsgPackDataException is a type of IOException, so throw it
			//seperately
			throw ex;
		} catch (IOException ex) {
			//this shouldn't happen
			throw new RuntimeException("ByteArrayInStream threw an IOException!", ex);
		}
	}


	protected static final int MAX_4BIT = 0xf;
	protected static final int MAX_5BIT = 0x1f;
	protected static final int MAX_7BIT = 0x7f;
	protected static final int MAX_8BIT = 0xff;
	protected static final int MAX_15BIT = 0x7fff;
	protected static final int MAX_16BIT = 0xffff;
	protected static final int MAX_31BIT = 0x7fffffff;
	protected static final long MAX_32BIT = 0xffffffffL;

	//these values are from http://wiki.msgpack.org/display/MSGPACK/Format+specification
	protected static final byte MP_NULL = (byte)0xc0;
	protected static final byte MP_FALSE = (byte)0xc2;
	protected static final byte MP_TRUE = (byte)0xc3;

	protected static final byte MP_FLOAT = (byte)0xca;
	protected static final byte MP_DOUBLE = (byte)0xcb;

	protected static final byte MP_FIXNUM = (byte)0x00;//last 7 bits is value
	protected static final byte MP_UINT8 = (byte)0xcc;
	protected static final byte MP_UINT16 = (byte)0xcd;
	protected static final byte MP_UINT32 = (byte)0xce;
	protected static final byte MP_UINT64 = (byte)0xcf;

	protected static final byte MP_NEGATIVE_FIXNUM = (byte)0xe0;//last 5 bits is value
	protected static final int MP_NEGATIVE_FIXNUM_INT = 0xe0;//  /me wishes for signed numbers.
	protected static final byte MP_INT8 = (byte)0xd0;
	protected static final byte MP_INT16 = (byte)0xd1;
	protected static final byte MP_INT32 = (byte)0xd2;
	protected static final byte MP_INT64 = (byte)0xd3;

	protected static final byte MP_FIXARRAY = (byte)0x90;//last 4 bits is size
	protected static final int MP_FIXARRAY_INT = 0x90;
	protected static final byte MP_ARRAY16 = (byte)0xdc;
	protected static final byte MP_ARRAY32 = (byte)0xdd;

	protected static final byte MP_FIXMAP = (byte)0x80;//last 4 bits is size
	protected static final int MP_FIXMAP_INT = 0x80;
	protected static final byte MP_MAP16 = (byte)0xde;
	protected static final byte MP_MAP32 = (byte)0xdf;

	protected static final byte MP_FIXRAW = (byte)0xa0;//last 5 bits is size
	protected static final int MP_FIXRAW_INT = 0xa0;
	protected static final byte MP_RAW16 = (byte)0xda;
	protected static final byte MP_RAW32 = (byte)0xdb;

	/**
	 * Packs the item, streaming the data to the given OutputStream.
	 * Warning: this does not do any recursion checks. If you pass a cyclic object,
	 * you will run in an infinite loop until you run out of memory/space to write.
	 * @param item
	 * @param out
	 */
	public static void pack(Object item, DataOutputStream out) throws IOException {
		if (item == null) {
			out.write(MP_NULL);
		} else if (item instanceof Boolean) {
			out.write((Boolean) item ? MP_TRUE : MP_FALSE);
		} else if (item instanceof Number) {
			if (item instanceof Float) {
				out.write(MP_FLOAT);
				out.writeFloat((Float)item);
			} else if (item instanceof Double) {
				out.write(MP_DOUBLE);
				out.writeDouble((Double)item);
			} else {
				long value = ((Number)item).longValue();
				if (value >= 0) {
					if (value <= MAX_7BIT) {
						out.write((int)value | MP_FIXNUM);
					} else if (value <= MAX_8BIT) {
						out.write(MP_UINT8);
						out.write((int)value);
					} else if (value <= MAX_16BIT) {
						out.write(MP_UINT16);
						out.writeShort((int)value);
					} else if (value <= MAX_32BIT) {
						out.write(MP_UINT32);
						out.writeInt((int)value);
					} else {
						out.write(MP_UINT64);
						out.writeLong(value);
					}
				} else {
					if (value >= -(MAX_5BIT + 1)) {
						out.write((int)(value & 0xff));
					} else if (value >= -(MAX_7BIT + 1)) {
						out.write(MP_INT8);
						out.write((int)value);
					} else if (value >= -(MAX_15BIT + 1)) {
						out.write(MP_INT16);
						out.writeShort((int)value);
					} else if (value >= -(MAX_31BIT + 1)) {
						out.write(MP_INT32);
						out.writeInt((int)value);
					} else {
						out.write(MP_INT64);
						out.writeLong(value);
					}
				}
			}
		} else if (item instanceof String || item instanceof byte[] || item instanceof ByteBuffer) {
			byte[] data;
			if (item instanceof String)
				data = ((String)item).getBytes("UTF-8");
			else if (item instanceof byte[])
				data = (byte[])item;
			else {
				ByteBuffer bb = ((ByteBuffer)item);
				if (bb.hasArray())
					data = bb.array();
				else {
					data = new byte[bb.capacity()];
					bb.position(0); bb.limit(bb.capacity());
					bb.get(data);
				}
			}

			if (data.length <= MAX_5BIT) {
				out.write(data.length | MP_FIXRAW);
			} else if (data.length <= MAX_16BIT) {
				out.write(MP_RAW16);
				out.writeShort(data.length);
			} else {
				out.write(MP_RAW32);
				out.writeInt(data.length);
			}
			out.write(data);
		} else if (item instanceof List) {
			List<Object> list = (List<Object>)item;
            int size = list.size();
            if (size <= MAX_4BIT) {
				out.write(size | MP_FIXARRAY);
			} else if (size <= MAX_16BIT) {
				out.write(MP_ARRAY16);
				out.writeShort(size);
			} else {
				out.write(MP_ARRAY32);
				out.writeInt(size);
			}
            for (int i = 0; i < size; i++) {
                Object element = list.get(i);
				pack(element, out);
			}
		} else if (item instanceof Map) {
			Map<Object, Object> map = (Map<Object, Object>)item;
            int size = map.size();
            if (size <= MAX_4BIT) {
				out.write(size | MP_FIXMAP);
			} else if (size <= MAX_16BIT) {
				out.write(MP_MAP16);
				out.writeShort(size);
			} else {
				out.write(MP_MAP32);
				out.writeInt(size);
			}
			for (Map.Entry<Object, Object> kvp : map.entrySet()) {
				pack(kvp.getKey(), out);
				pack(kvp.getValue(), out);
			}
		} else {
			throw new IllegalArgumentException("Cannot msgpack object of type " + item.getClass().getCanonicalName());
		}
	}

	/**
	 * Packs the item, streaming the data to the given OutputStream.
	 * Warning: this does not do any recursion checks. If you pass a cyclic object,
	 * you will run in an infinite loop until you run out of memory/space to write.
	 * @param in Input stream to read from
	 * @param options Bitmask of options, @see unpack(byte[] data, int options)
	 * @throws IOException if the underlying stream has an error
	 * @throws InvalidMsgPackDataException If the given data cannot be unpacked.
	 */
	public static Object unpack(DataInputStream in, int options) throws IOException {
		int value = in.read();
		if (value < 0) throw new InvalidMsgPackDataException("No more input available when expecting a value");

		try {
			int size;
			switch ((byte)value) {
				case MP_NULL:
					return null;
				case MP_FALSE:
					return false;
				case MP_TRUE:
					return true;
				case MP_FLOAT:
					return in.readFloat();
				case MP_DOUBLE:
					return in.readDouble();
				case MP_UINT8:
					return in.read();//read single byte, return as int
				case MP_UINT16:
					return in.readShort() & MAX_16BIT;//read short, trick Java into treating it as unsigned, return int
				case MP_UINT32:
					return in.readInt() & MAX_32BIT;//read int, trick Java into treating it as unsigned, return long
				case MP_UINT64: {
					long v = in.readLong();
					if (v >= 0) return v;
					else {
						//this is a little bit more tricky, since we don't have unsigned longs
						byte[] bytes = new byte[]{
							(byte)((v >> 24) & 0xff),
							(byte)((v >> 16) & 0xff),
							(byte)((v >> 8) & 0xff),
							(byte)(v & 0xff),
						};
						return new BigInteger(1, bytes);
					}
				}
				case MP_INT8:
					return (byte)in.read();
				case MP_INT16:
					return in.readShort();
				case MP_INT32:
					return in.readInt();
				case MP_INT64:
					return in.readLong();
				case MP_ARRAY16:
					return unpackList(in.readShort() & MAX_16BIT, in, options);
				case MP_ARRAY32:
					return unpackList(in.readInt(), in, options);
				case MP_MAP16:
					return unpackMap(in.readShort() & MAX_16BIT, in, options);
				case MP_MAP32:
					return unpackMap(in.readInt(), in, options);
				case MP_RAW16:
					return unpackRaw(in.readShort() & MAX_16BIT, in, options);
				case MP_RAW32:
					return unpackRaw(in.readInt(), in, options);
			}

			if (value >= MP_NEGATIVE_FIXNUM_INT && value <= MP_NEGATIVE_FIXNUM_INT + MAX_5BIT) {
				return (byte)value;
			} else if (value >= MP_FIXARRAY_INT && value <= MP_FIXARRAY_INT + MAX_4BIT) {
				return unpackList(value - MP_FIXARRAY_INT, in, options);
			} else if (value >= MP_FIXMAP_INT && value <= MP_FIXMAP_INT + MAX_4BIT) {
				return unpackMap(value - MP_FIXMAP_INT, in, options);
			} else if (value >= MP_FIXRAW_INT && value <= MP_FIXRAW_INT + MAX_5BIT) {
				return unpackRaw(value - MP_FIXRAW_INT, in, options);
			} else if (value <= MAX_7BIT) {//MP_FIXNUM - the value is value as an int
				return value;
			} else {
				throw new InvalidMsgPackDataException("Input contains invalid type value");
			}
		} catch (EOFException ex) {
			throw new InvalidMsgPackDataException("No more input available when expecting a value");
		}
	}

	protected static List unpackList(int size, DataInputStream in, int options) throws IOException {
		if (size < 0) throw new InvalidMsgPackDataException("Array to unpack too large for Java (more than 2^31 elements)!");
		List ret = new ArrayList(size);
		for (int i = 0; i < size; ++i) {
			ret.add(unpack(in, options));
		}
		return ret;
	}

	protected static Map unpackMap(int size, DataInputStream in, int options) throws IOException {
		if (size < 0) throw new InvalidMsgPackDataException("Map to unpack too large for Java (more than 2^31 elements)!");
		Map ret = new HashMap(size);
		for (int i = 0; i < size; ++i) {
			Object key = unpack(in, options);
			Object value = unpack(in, options);
			ret.put(key, value);
		}
		return ret;
	}

	protected static Object unpackRaw(int size, DataInputStream in, int options) throws IOException {
		if (size < 0) throw new InvalidMsgPackDataException("byte[] to unpack too large for Java (more than 2^31 elements)!");

		byte[] data = new byte[size];
		in.read(data);

		if ((options & UNPACK_RAW_AS_BYTE_BUFFER) != 0) {
			return ByteBuffer.wrap(data);
		} else if ((options & UNPACK_RAW_AS_STRING) != 0) {
			return new String(data, "UTF-8");
		} else {
			return data;
		}
	}
}

class InvalidMsgPackDataException extends IOException {
	public InvalidMsgPackDataException() {
		super();
	}
	public InvalidMsgPackDataException(String message) {
		super(message);
	}
	public InvalidMsgPackDataException(String message, Throwable cause) {
		super(message, cause);
	}
	public InvalidMsgPackDataException(Throwable cause) {
		super(cause);
	}
}
class UnpackableItemException extends IllegalArgumentException {
	public UnpackableItemException() {
		super();
	}
	public UnpackableItemException(String message) {
		super(message);
	}
	public UnpackableItemException(String message, Throwable cause) {
		super(message, cause);
	}
	public UnpackableItemException(Throwable cause) {
		super(cause);
	}
}
