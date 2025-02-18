package io.milvus.response;

import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.ProtocolStringList;
import io.milvus.exception.ParamException;
import io.milvus.grpc.ArrayArray;
import io.milvus.grpc.DataType;
import io.milvus.grpc.FieldData;
import io.milvus.exception.IllegalResponseException;

import io.milvus.grpc.ScalarField;
import lombok.NonNull;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.protobuf.ByteString;

import static io.milvus.grpc.DataType.JSON;

/**
 * Utility class to wrap response of <code>query/search</code> interface.
 */
public class FieldDataWrapper {
    private final FieldData fieldData;

    public FieldDataWrapper(@NonNull FieldData fieldData) {
        this.fieldData = fieldData;
    }

    public boolean isVectorField() {
        return fieldData.getType() == DataType.FloatVector || fieldData.getType() == DataType.BinaryVector;
    }

    public boolean isJsonField() {
        return fieldData.getType() == JSON;
    }

    public boolean isDynamicField() {
        return fieldData.getType() == JSON && fieldData.getIsDynamic();
    }

    /**
     * Gets the dimension value of a vector field.
     * Throw {@link IllegalResponseException} if the field is not a vector filed.
     *
     * @return <code>int</code> dimension of the vector field
     */
    public int getDim() throws IllegalResponseException {
        if (!isVectorField()) {
            throw new IllegalResponseException("Not a vector field");
        }
        return (int) fieldData.getVectors().getDim();
    }

    /**
     * Gets the row count of a field.
     * * Throws {@link IllegalResponseException} if the field type is illegal.
     *
     * @return <code>long</code> row count of the field
     */
    public long getRowCount() throws IllegalResponseException {
        DataType dt = fieldData.getType();
        switch (dt) {
            case FloatVector: {
                int dim = getDim();
                List<Float> data = fieldData.getVectors().getFloatVector().getDataList();
                if (data.size() % dim != 0) {
                    throw new IllegalResponseException("Returned float vector field data array size doesn't match dimension");
                }

                return data.size()/dim;
            }
            case BinaryVector: {
                int dim = getDim();
                ByteString data = fieldData.getVectors().getBinaryVector();
                if ((data.size()*8) % dim != 0) {
                    throw new IllegalResponseException("Returned binary vector field data array size doesn't match dimension");
                }

                return (data.size()*8)/dim;
            }
            case Int64:
                return fieldData.getScalars().getLongData().getDataCount();
            case Int32:
            case Int16:
            case Int8:
                return fieldData.getScalars().getIntData().getDataCount();
            case Bool:
                return fieldData.getScalars().getBoolData().getDataCount();
            case Float:
                return fieldData.getScalars().getFloatData().getDataCount();
            case Double:
                return fieldData.getScalars().getDoubleData().getDataCount();
            case VarChar:
            case String:
                return fieldData.getScalars().getStringData().getDataCount();
            case JSON:
                return fieldData.getScalars().getJsonData().getDataCount();
            case Array:
                return fieldData.getScalars().getArrayData().getDataCount();
            default:
                throw new IllegalResponseException("Unsupported data type returned by FieldData");
        }
    }

    /**
     * Returns the field data according to its type:
     *      float vector field return List of List Float,
     *      binary vector field return List of ByteBuffer
     *      int64 field return List of Long
     *      int32/int16/int8 field return List of Integer
     *      boolean field return List of Boolean
     *      float field return List of Float
     *      double field return List of Double
     *      varchar field return List of String
     *      array field return List of List
     *      etc.
     *
     * Throws {@link IllegalResponseException} if the field type is illegal.
     *
     * @return <code>List</code>
     */
    public List<?> getFieldData() throws IllegalResponseException {
        DataType dt = fieldData.getType();
        switch (dt) {
            case FloatVector: {
                int dim = getDim();
                List<Float> data = fieldData.getVectors().getFloatVector().getDataList();
                if (data.size() % dim != 0) {
                    throw new IllegalResponseException("Returned float vector field data array size doesn't match dimension");
                }

                List<List<Float>> packData = new ArrayList<>();
                int count = data.size() / dim;
                for (int i = 0; i < count; ++i) {
                    packData.add(data.subList(i * dim, (i + 1) * dim));
                }
                return packData;
            }
            case BinaryVector: {
                int dim = getDim();
                ByteString data = fieldData.getVectors().getBinaryVector();
                if ((data.size()*8) % dim != 0) {
                    throw new IllegalResponseException("Returned binary vector field data array size doesn't match dimension");
                }

                List<ByteBuffer> packData = new ArrayList<>();
                int bytePerVec = dim/8;
                int count = data.size()/bytePerVec;
                for (int i = 0; i < count; ++i) {
                    ByteBuffer bf = ByteBuffer.allocate(bytePerVec);
                    bf.put(data.substring(i * bytePerVec, (i + 1) * bytePerVec).toByteArray());
                    packData.add(bf);
                }
                return packData;
            }
            case Array:
                List<List<?>> array = new ArrayList<>();
                ArrayArray arrArray = fieldData.getScalars().getArrayData();
                for (int i = 0; i < arrArray.getDataCount(); i++) {
                    ScalarField scalar = arrArray.getData(i);
                    array.add(getScalarData(arrArray.getElementType(), scalar));
                }
                return array;
            case Int64:
            case Int32:
            case Int16:
            case Int8:
            case Bool:
            case Float:
            case Double:
            case VarChar:
            case String:
            case JSON:
                return getScalarData(dt, fieldData.getScalars());
            default:
                throw new IllegalResponseException("Unsupported data type returned by FieldData");
        }
    }

    private List<?> getScalarData(DataType dt, ScalarField scalar) {
        switch (dt) {
            case Int64:
                return scalar.getLongData().getDataList();
            case Int32:
            case Int16:
            case Int8:
                return scalar.getIntData().getDataList();
            case Bool:
                return scalar.getBoolData().getDataList();
            case Float:
                return scalar.getFloatData().getDataList();
            case Double:
                return scalar.getDoubleData().getDataList();
            case VarChar:
            case String:
                ProtocolStringList protoStrList = scalar.getStringData().getDataList();
                return protoStrList.subList(0, protoStrList.size());
            case JSON:
                List<ByteString> dataList = scalar.getJsonData().getDataList();
                return dataList.stream().map(ByteString::toByteArray).collect(Collectors.toList());
            default:
                return new ArrayList<>();
        }
    }

    public Integer getAsInt(int index, String paramName) throws IllegalResponseException {
        if (isJsonField()) {
            String result = getAsString(index, paramName);
            return result == null ? null : Integer.parseInt(result);
        }
        throw new IllegalResponseException("Only JSON type support this operation");
    }

    public String getAsString(int index, String paramName) throws IllegalResponseException {
        if (isJsonField()) {
            JSONObject jsonObject = parseObjectData(index);
            return jsonObject.getString(paramName);
        }
        throw new IllegalResponseException("Only JSON type support this operation");
    }

    public Boolean getAsBool(int index, String paramName) throws IllegalResponseException {
        if (isJsonField()) {
            String result = getAsString(index, paramName);
            return result == null ? null : Boolean.parseBoolean(result);
        }
        throw new IllegalResponseException("Only JSON type support this operation");
    }

    public Double getAsDouble(int index, String paramName) throws IllegalResponseException {
        if (isJsonField()) {
            String result = getAsString(index, paramName);
            return result == null ? null : Double.parseDouble(result);
        }
        throw new IllegalResponseException("Only JSON type support this operation");
    }

    public Object get(int index, String paramName) throws IllegalResponseException {
        if (isJsonField()) {
            JSONObject jsonObject = parseObjectData(index);
            return jsonObject.get(paramName);
        }
        throw new IllegalResponseException("Only JSON type support this operation");
    }

    public Object valueByIdx(int index) throws ParamException {
        if (index < 0 || index >= getFieldData().size()) {
            throw new ParamException("index out of range");
        }
        return getFieldData().get(index);
    }

    private JSONObject parseObjectData(int index) {
        Object object = valueByIdx(index);
        return JSONObject.parseObject(new String((byte[])object));
    }
}
