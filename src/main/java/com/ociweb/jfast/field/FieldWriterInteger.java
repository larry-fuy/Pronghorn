//Copyright 2013, Nathan Tippy
//See LICENSE file for BSD license details.
//Send support requests to http://www.ociweb.com/contact
package com.ociweb.jfast.field;

import com.ociweb.jfast.loader.DictionaryFactory;
import com.ociweb.jfast.primitive.PrimitiveWriter;

public final class FieldWriterInteger {
	
	
	//for optional fields it is still in the optional format so 
	//zero represents null for those fields.  
	final int[]  lastValue;
	private final PrimitiveWriter writer;
	private final int INSTANCE_MASK;
	
	public FieldWriterInteger(PrimitiveWriter writer, int[] values) {
		assert(values.length<TokenBuilder.MAX_INSTANCE);
		assert(FieldReaderInteger.isPowerOfTwo(values.length));
		
		this.INSTANCE_MASK = Math.min(TokenBuilder.MAX_INSTANCE, (values.length-1));
		this.writer = writer;
		this.lastValue = values;
	}
	
	public void reset(DictionaryFactory df) {
		df.reset(lastValue);
	}	
	public void copy(int sourceToken, int targetToken) {
		lastValue[targetToken & INSTANCE_MASK] = lastValue[sourceToken & INSTANCE_MASK];
	}
	
	public void flush() {
		writer.flush();
	}
	
	
	/*
	 * Method name convention to group the work 
	 *  write <FIELD_TYPE><OPERATOR>
	 *  
	 *  example FIELD_TYPES 
	 *  IntegerSigned
	 *  IntegerUnsigned
	 *  IntegerSingedOptional
	 *  IntegerUnsignedOptional
	 * 
	 */
	
	public void writeIntegerUnsigned(int value, int token) {
		//int idx = token & INSTANCE_MASK;
		//lastValue[idx] = value;//TODO: not sure if this feature will be needed.
		writer.writeIntegerUnsigned(value);
	}
	
	public void writeIntegerUnsignedCopy(int value, int token) {
		if (value == lastValue[token & INSTANCE_MASK]) {
			writer.writePMapBit((byte)0);
		} else {
			writer.writePMapBit((byte)1);
			writer.writeIntegerUnsigned(value);
			lastValue[token & INSTANCE_MASK] = value;
		}
	}
	
	public void writeIntegerUnsignedCopyOptional(int value, int token) {
		//zero is reserved for null
		if (++value == lastValue[token & INSTANCE_MASK]) {//not null and matches
			writer.writePMapBit((byte)0);
		} else {
			writer.writePMapBit((byte)1);
			writer.writeIntegerUnsigned(lastValue[token & INSTANCE_MASK] = value);
		}
	}
	

	
	public void writeIntegerUnsignedConstant(int value, int token) {
		assert(lastValue[ token & INSTANCE_MASK]==value) : "Only the constant value from the template may be sent";
		//nothing need be sent because constant does not use pmap and the template
		//on the other receiver side will inject this value from the template
	}
	
	public void writeIntegerUnsignedConstantOptional(int value, int token) {
		writer.writePMapBit((byte)1);
		assert(lastValue[ token & INSTANCE_MASK]==value) : "Only the constant value from the template may be sent";
		//the writeNull will take care of the rest.
	}
	
	
	public void writeIntegerSignedConstant(int value, int token) {
//TODO: unit test error.		assert(lastValue[ token & INSTANCE_MASK]==value) : "Only the constant value from the template may be sent";
		//nothing need be sent because constant does not use pmap and the template
		//on the other receiver side will inject this value from the template
	}
	
	public void writeIntegerSignedConstantOptional(int value, int token) {
		writer.writePMapBit((byte)1);
		assert(lastValue[ token & INSTANCE_MASK]==value) : "Only the constant value from the template may be sent";
		//the writeNull will take care of the rest.
	}
	
	public void writeIntegerUnsignedDefault(int value, int token) {
		int idx = token & INSTANCE_MASK;

		if (value == lastValue[idx]) {
			writer.writePMapBit((byte)0);
		} else {
			writer.writePMapBit((byte)1);
			writer.writeIntegerUnsigned(value);
		}
	}
	
	public void writeIntegerUnsignedDefaultOptional(int value, int token) {
		//room for zero so we add one first
		if (++value == lastValue[token & INSTANCE_MASK]) {//not null and matches
			writer.writePMapBit((byte)0);
		} else {
			writer.writePMapBit((byte)1);
			writer.writeIntegerUnsigned(value);
		}
	}


	
	public void writeIntegerUnsignedIncrement(int value, int token) {
		int idx;
		int incVal;
		
		if (value == (incVal = lastValue[idx = token & INSTANCE_MASK]+1)) {
			writer.writePMapBit((byte)0);
			lastValue[idx] = incVal;
		} else {
			writer.writePMapBit((byte)1);
			writer.writeIntegerUnsigned(lastValue[idx] = value);
		}
	}
	

	public void writeIntegerUnsignedIncrementOptional(int value, int token) {

		int idx = token & INSTANCE_MASK;

		if (0!=lastValue[idx] && value == lastValue[idx]++) {//not null and matches
			writer.writePMapBit((byte)0);
		} else {
			writer.writePMapBit((byte)1);
			writer.writeIntegerUnsigned(lastValue[idx] = 1+value);
		}
	}
	

	

	public void writeIntegerUnsignedDelta(int value, int token) {
		//Delta opp never uses PMAP
		int idx;
		writer.writeIntegerSigned(value - lastValue[idx = token & INSTANCE_MASK]);
		lastValue[idx] = value;		
	}
	
	public void writeIntegerUnsignedDeltaOptional(int value, int token) {
		//Delta opp never uses PMAP
		int idx;
		int delta = value - lastValue[idx = token & INSTANCE_MASK];
		writer.writeLongSigned(delta>=0?1+delta:delta);
		lastValue[idx] = value;	
	}
	

	////////////////
	///////////////
	////////////////
	
	public void writeIntegerSigned(int value, int token) {
		//int idx = token & INSTANCE_MASK;
		//lastValue[idx] = value;//TODO: not sure if this feature will be needed.
		writer.writeIntegerSigned(value);
	}
	
	public void writeIntegerSignedCopy(int value, int token) {
		int idx = token & INSTANCE_MASK;

		if (value == lastValue[idx]) {
			writer.writePMapBit((byte)0);
		} else {
			writer.writePMapBit((byte)1);
			writer.writeIntegerSigned(value);
			lastValue[idx] = value;
		}
	}
	
	public void writeIntegerSignedCopyOptional(int value, int token) {
		int idx = token & INSTANCE_MASK;

		if (value>=0) {
			value++;
		}
		
		if (value == lastValue[idx]) {//not null and matches
			writer.writePMapBit((byte)0);
		} else {
			writer.writePMapBit((byte)1);
			writer.writeIntegerSigned(lastValue[idx] = value);
		}
	}
	

	public void writeIntegerSignedDefault(int value, int token) {
		int idx = token & INSTANCE_MASK;

		if (value == lastValue[idx]) {
			writer.writePMapBit((byte)0);
		} else {
			writer.writePMapBit((byte)1);
			writer.writeIntegerSigned(value);
		}
	}
	
	public void writeIntegerSignedDefaultOptional(int value, int token) {
		if (value>=0) {
			value++;//room for null
		}		
		if (value == lastValue[token & INSTANCE_MASK]) {//matches
			writer.writePMapBit((byte)0);
		} else {
			writer.writePMapBit((byte)1);
			writer.writeIntegerSigned(value);
		}
	}
	

	
	public void writeIntegerSignedIncrement(int value, int token) {
		int idx = token & INSTANCE_MASK;
		int incVal = lastValue[idx]+1;
		
		if (value == incVal) {
			writer.writePMapBit((byte)0);
			lastValue[idx] = incVal;
		} else {
			writer.writePMapBit((byte)1);
			writer.writeIntegerSigned(lastValue[idx] = value);
		}
	}
	

	public void writeIntegerSignedIncrementOptional(int value, int token) {

		int idx = token & INSTANCE_MASK;

		if (value>=0) {
			value++;
		}
		if (0!=lastValue[idx] && 
			value == ++lastValue[idx]) {//not null and matches
			writer.writePMapBit((byte)0);
		} else {
			writer.writePMapBit((byte)1);
			writer.writeIntegerSigned(lastValue[idx] = value);
		}
			
	}


	public void writeIntegerSignedDelta(int value, int token) {
		//Delta opp never uses PMAP
		int idx = token & INSTANCE_MASK;
		writer.writeIntegerSigned(value - lastValue[idx]);
		lastValue[idx] = value;		
	}
	
	public void writeIntegerSignedDeltaOptional(int value, int token) {
		//Delta opp never uses PMAP
		int idx = token & INSTANCE_MASK;
		long dif = value - lastValue[idx];
		//writer.writeLongSigned((dif + (1-(dif>>>63)) ));
		writer.writeLongSigned(dif>=0 ? 1+dif : dif);
		lastValue[idx] = value;	
	}

	public void writeNull(int token) {
		
		if (0==(token&(2<<TokenBuilder.SHIFT_OPER))) {
			if (0==(token&(1<<TokenBuilder.SHIFT_OPER))) {
				//None and Delta (both do not use pmap)
				writeClearNull(token);              //no pmap, yes change to last value
			} else {
				//Copy and Increment
				writePMapAndClearNull(token);  //yes pmap, yes change to last value	
			}
		} else {
			if (0==(token&(1<<TokenBuilder.SHIFT_OPER))) {
				assert(0!=(token&(1<<TokenBuilder.SHIFT_TYPE))) :"Sending a null constant is not supported";
				//const optional
				writer.writePMapBit((byte)0);       //pmap only
			} else {	
				//default
				writePMapNull(token);  //yes pmap,  no change to last value
			}	
		}
		
	}
	
	private void writeClearNull(int token) {
		writer.writeNull();
		lastValue[token & INSTANCE_MASK] = 0;
	}
	
	
	private void writePMapAndClearNull(int token) {
		int idx = token & INSTANCE_MASK;

		if (lastValue[idx]==0) { //stored value was null;
			writer.writePMapBit((byte)0);
		} else {
			writer.writePMapBit((byte)1);
			writer.writeNull();
			lastValue[idx] =0;
		}
	}
	
	
	private void writePMapNull(int token) {
		if (lastValue[token & INSTANCE_MASK]==0) { //stored value was null;
			writer.writePMapBit((byte)0);
		} else {
			writer.writePMapBit((byte)1);
			writer.writeNull();
		}
	}

	public void writeIntegerSignedOptional(int value, int token) {
		writer.writeIntegerSignedOptional(value);
	}

	public void writerIntegerUnsignedOptional(int value, int token) {
		writer.writeIntegerUnsigned(value+1);
	}
	
}
