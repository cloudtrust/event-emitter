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
public final class AuthDetails extends Table {
  public static void ValidateVersion() { Constants.FLATBUFFERS_25_2_10(); }
  public static AuthDetails getRootAsAuthDetails(ByteBuffer _bb) { return getRootAsAuthDetails(_bb, new AuthDetails()); }
  public static AuthDetails getRootAsAuthDetails(ByteBuffer _bb, AuthDetails obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { __reset(_i, _bb); }
  public AuthDetails __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public String realmId() { int o = __offset(4); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer realmIdAsByteBuffer() { return __vector_as_bytebuffer(4, 1); }
  public ByteBuffer realmIdInByteBuffer(ByteBuffer _bb) { return __vector_in_bytebuffer(_bb, 4, 1); }
  public String clientId() { int o = __offset(6); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer clientIdAsByteBuffer() { return __vector_as_bytebuffer(6, 1); }
  public ByteBuffer clientIdInByteBuffer(ByteBuffer _bb) { return __vector_in_bytebuffer(_bb, 6, 1); }
  public String userId() { int o = __offset(8); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer userIdAsByteBuffer() { return __vector_as_bytebuffer(8, 1); }
  public ByteBuffer userIdInByteBuffer(ByteBuffer _bb) { return __vector_in_bytebuffer(_bb, 8, 1); }
  public String username() { int o = __offset(10); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer usernameAsByteBuffer() { return __vector_as_bytebuffer(10, 1); }
  public ByteBuffer usernameInByteBuffer(ByteBuffer _bb) { return __vector_in_bytebuffer(_bb, 10, 1); }
  public String ipAddress() { int o = __offset(12); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer ipAddressAsByteBuffer() { return __vector_as_bytebuffer(12, 1); }
  public ByteBuffer ipAddressInByteBuffer(ByteBuffer _bb) { return __vector_in_bytebuffer(_bb, 12, 1); }

  public static int createAuthDetails(FlatBufferBuilder builder,
      int realmIdOffset,
      int clientIdOffset,
      int userIdOffset,
      int usernameOffset,
      int ipAddressOffset) {
    builder.startTable(5);
    AuthDetails.addIpAddress(builder, ipAddressOffset);
    AuthDetails.addUsername(builder, usernameOffset);
    AuthDetails.addUserId(builder, userIdOffset);
    AuthDetails.addClientId(builder, clientIdOffset);
    AuthDetails.addRealmId(builder, realmIdOffset);
    return AuthDetails.endAuthDetails(builder);
  }

  public static void startAuthDetails(FlatBufferBuilder builder) { builder.startTable(5); }
  public static void addRealmId(FlatBufferBuilder builder, int realmIdOffset) { builder.addOffset(0, realmIdOffset, 0); }
  public static void addClientId(FlatBufferBuilder builder, int clientIdOffset) { builder.addOffset(1, clientIdOffset, 0); }
  public static void addUserId(FlatBufferBuilder builder, int userIdOffset) { builder.addOffset(2, userIdOffset, 0); }
  public static void addUsername(FlatBufferBuilder builder, int usernameOffset) { builder.addOffset(3, usernameOffset, 0); }
  public static void addIpAddress(FlatBufferBuilder builder, int ipAddressOffset) { builder.addOffset(4, ipAddressOffset, 0); }
  public static int endAuthDetails(FlatBufferBuilder builder) {
    int o = builder.endTable();
    return o;
  }

  public static final class Vector extends BaseVector {
    public Vector __assign(int _vector, int _element_size, ByteBuffer _bb) { __reset(_vector, _element_size, _bb); return this; }

    public AuthDetails get(int j) { return get(new AuthDetails(), j); }
    public AuthDetails get(AuthDetails obj, int j) {  return obj.__assign(__indirect(__element(j), bb), bb); }
  }
}

