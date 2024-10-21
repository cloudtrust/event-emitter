// automatically generated by the FlatBuffers compiler, do not modify

package flatbuffers.events;

import com.google.flatbuffers.BaseVector;
import com.google.flatbuffers.BooleanVector;
import com.google.flatbuffers.ByteVector;
import com.google.flatbuffers.Constants;
import com.google.flatbuffers.DoubleVector;
import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.FloatVector;
import com.google.flatbuffers.IntVector;
import com.google.flatbuffers.LongVector;
import com.google.flatbuffers.ShortVector;
import com.google.flatbuffers.StringVector;
import com.google.flatbuffers.Struct;
import com.google.flatbuffers.Table;
import com.google.flatbuffers.UnionVector;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@SuppressWarnings("unused")
public final class AdminEvent extends Table {
  public static void ValidateVersion() { Constants.FLATBUFFERS_24_3_25(); }
  public static AdminEvent getRootAsAdminEvent(ByteBuffer _bb) { return getRootAsAdminEvent(_bb, new AdminEvent()); }
  public static AdminEvent getRootAsAdminEvent(ByteBuffer _bb, AdminEvent obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { __reset(_i, _bb); }
  public AdminEvent __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public long uid() { int o = __offset(4); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }
  public long time() { int o = __offset(6); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }
  public String realmId() { int o = __offset(8); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer realmIdAsByteBuffer() { return __vector_as_bytebuffer(8, 1); }
  public ByteBuffer realmIdInByteBuffer(ByteBuffer _bb) { return __vector_in_bytebuffer(_bb, 8, 1); }
  public flatbuffers.events.AuthDetails authDetails() { return authDetails(new flatbuffers.events.AuthDetails()); }
  public flatbuffers.events.AuthDetails authDetails(flatbuffers.events.AuthDetails obj) { int o = __offset(10); return o != 0 ? obj.__assign(__indirect(o + bb_pos), bb) : null; }
  public flatbuffers.events.Tuple details(int j) { return details(new flatbuffers.events.Tuple(), j); }
  public flatbuffers.events.Tuple details(flatbuffers.events.Tuple obj, int j) { int o = __offset(12); return o != 0 ? obj.__assign(__indirect(__vector(o) + j * 4), bb) : null; }
  public int detailsLength() { int o = __offset(12); return o != 0 ? __vector_len(o) : 0; }
  public flatbuffers.events.Tuple.Vector detailsVector() { return detailsVector(new flatbuffers.events.Tuple.Vector()); }
  public flatbuffers.events.Tuple.Vector detailsVector(flatbuffers.events.Tuple.Vector obj) { int o = __offset(12); return o != 0 ? obj.__assign(__vector(o), 4, bb) : null; }
  public byte resourceType() { int o = __offset(14); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public byte operationType() { int o = __offset(16); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public String resourcePath() { int o = __offset(18); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer resourcePathAsByteBuffer() { return __vector_as_bytebuffer(18, 1); }
  public ByteBuffer resourcePathInByteBuffer(ByteBuffer _bb) { return __vector_in_bytebuffer(_bb, 18, 1); }
  public String representation() { int o = __offset(20); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer representationAsByteBuffer() { return __vector_as_bytebuffer(20, 1); }
  public ByteBuffer representationInByteBuffer(ByteBuffer _bb) { return __vector_in_bytebuffer(_bb, 20, 1); }
  public String error() { int o = __offset(22); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer errorAsByteBuffer() { return __vector_as_bytebuffer(22, 1); }
  public ByteBuffer errorInByteBuffer(ByteBuffer _bb) { return __vector_in_bytebuffer(_bb, 22, 1); }

  public static int createAdminEvent(FlatBufferBuilder builder,
      long uid,
      long time,
      int realmIdOffset,
      int authDetailsOffset,
      int detailsOffset,
      byte resourceType,
      byte operationType,
      int resourcePathOffset,
      int representationOffset,
      int errorOffset) {
    builder.startTable(10);
    AdminEvent.addTime(builder, time);
    AdminEvent.addUid(builder, uid);
    AdminEvent.addError(builder, errorOffset);
    AdminEvent.addRepresentation(builder, representationOffset);
    AdminEvent.addResourcePath(builder, resourcePathOffset);
    AdminEvent.addDetails(builder, detailsOffset);
    AdminEvent.addAuthDetails(builder, authDetailsOffset);
    AdminEvent.addRealmId(builder, realmIdOffset);
    AdminEvent.addOperationType(builder, operationType);
    AdminEvent.addResourceType(builder, resourceType);
    return AdminEvent.endAdminEvent(builder);
  }

  public static void startAdminEvent(FlatBufferBuilder builder) { builder.startTable(10); }
  public static void addUid(FlatBufferBuilder builder, long uid) { builder.addLong(0, uid, 0L); }
  public static void addTime(FlatBufferBuilder builder, long time) { builder.addLong(1, time, 0L); }
  public static void addRealmId(FlatBufferBuilder builder, int realmIdOffset) { builder.addOffset(2, realmIdOffset, 0); }
  public static void addAuthDetails(FlatBufferBuilder builder, int authDetailsOffset) { builder.addOffset(3, authDetailsOffset, 0); }
  public static void addDetails(FlatBufferBuilder builder, int detailsOffset) { builder.addOffset(4, detailsOffset, 0); }
  public static int createDetailsVector(FlatBufferBuilder builder, int[] data) { builder.startVector(4, data.length, 4); for (int i = data.length - 1; i >= 0; i--) builder.addOffset(data[i]); return builder.endVector(); }
  public static void startDetailsVector(FlatBufferBuilder builder, int numElems) { builder.startVector(4, numElems, 4); }
  public static void addResourceType(FlatBufferBuilder builder, byte resourceType) { builder.addByte(5, resourceType, 0); }
  public static void addOperationType(FlatBufferBuilder builder, byte operationType) { builder.addByte(6, operationType, 0); }
  public static void addResourcePath(FlatBufferBuilder builder, int resourcePathOffset) { builder.addOffset(7, resourcePathOffset, 0); }
  public static void addRepresentation(FlatBufferBuilder builder, int representationOffset) { builder.addOffset(8, representationOffset, 0); }
  public static void addError(FlatBufferBuilder builder, int errorOffset) { builder.addOffset(9, errorOffset, 0); }
  public static int endAdminEvent(FlatBufferBuilder builder) {
    int o = builder.endTable();
    return o;
  }
  public static void finishAdminEventBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }
  public static void finishSizePrefixedAdminEventBuffer(FlatBufferBuilder builder, int offset) { builder.finishSizePrefixed(offset); }

  public static final class Vector extends BaseVector {
    public Vector __assign(int _vector, int _element_size, ByteBuffer _bb) { __reset(_vector, _element_size, _bb); return this; }

    public AdminEvent get(int j) { return get(new AdminEvent(), j); }
    public AdminEvent get(AdminEvent obj, int j) {  return obj.__assign(__indirect(__element(j), bb), bb); }
  }
}

