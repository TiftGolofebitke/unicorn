package unicorn.json

import java.math.BigInteger
import java.util.Date
import scala.util.Try

/**
 * ObjectId is a 12-byte BSON type, constructed using:
 * a 4-byte value representing the seconds since the Unix epoch,
 * a 3-byte machine identifier,
 * a 2-byte process id, and
 * a 3-byte counter, starting with a random value.
 *
 * The implementation is adopt from ReactiveMongo.
 */
case class BsonObjectId(id: Array[Byte]) {
  require(id.size == BsonObjectId.size)
  import java.util.Arrays
  import java.nio.ByteBuffer

  /** Hexadecimal string representation */
  lazy val str = JsBinary.bytes2Hex(id)

  /** In the form of a string literal "ObjectId(...)" */
  override def toString = s"""ObjectId(${str})"""

  override def equals(that: Any): Boolean = {
    that.isInstanceOf[BsonObjectId] && Arrays.equals(id, that.asInstanceOf[BsonObjectId].id)
  }

  override lazy val hashCode: Int = Arrays.hashCode(id)

  /** The timestamp port of ObjectId object as a Date */
  def getTimestamp: Date = new Date(ByteBuffer.wrap(id.take(4)).getInt * 1000L)

  /** Copy the bytes */
  def copyBytes = Arrays.copyOf(id, BsonObjectId.size)
}

object BsonObjectId {
  /** ObjectId byte array size */
  val size = 12

  private val maxCounterValue = 16777216
  private val increment = new java.util.concurrent.atomic.AtomicInteger(scala.util.Random.nextInt(maxCounterValue))

  private def counter = (increment.getAndIncrement + maxCounterValue) % maxCounterValue

  /**
   * The following implementation of machineId work around openjdk limitations in
   * version 6 and 7
   *
   * Openjdk fails to parse /proc/net/if_inet6 correctly to determine macaddress
   * resulting in SocketException thrown.
   *
   * Please see:
   * * https://github.com/openjdk-mirror/jdk7u-jdk/blob/feeaec0647609a1e6266f902de426f1201f77c55/src/solaris/native/java/net/NetworkInterface.c#L1130
   * * http://lxr.free-electrons.com/source/net/ipv6/addrconf.c?v=3.11#L3442
   * * http://lxr.free-electrons.com/source/include/linux/netdevice.h?v=3.11#L1130
   * * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7078386
   *
   * and fix in openjdk8:
   * * http://hg.openjdk.java.net/jdk8/tl/jdk/rev/b1814b3ea6d3
   */
  private val machineId = {
    import java.net._
    val validPlatform = Try {
      val correctVersion = System.getProperty("java.version").substring(0, 3).toFloat >= 1.8
      val noIpv6 = System.getProperty("java.net.preferIPv4Stack") == true
      val isLinux = System.getProperty("os.name") == "Linux"

      !isLinux || correctVersion || noIpv6
    }.getOrElse(false)

    // Check java policies
    val permitted = {
      val sec = System.getSecurityManager();
      Try { sec.checkPermission(new NetPermission("getNetworkInformation")) }.toOption.map(_ => true).getOrElse(false);
    }

    if (validPlatform && permitted) {
      val networkInterfacesEnum = NetworkInterface.getNetworkInterfaces
      val networkInterfaces = scala.collection.JavaConverters.enumerationAsScalaIteratorConverter(networkInterfacesEnum).asScala
      val ha = networkInterfaces.find(ha => Try(ha.getHardwareAddress).isSuccess && ha.getHardwareAddress != null && ha.getHardwareAddress.length == 6)
        .map(_.getHardwareAddress)
        .getOrElse(InetAddress.getLocalHost.getHostName.getBytes)
      md5(ha).take(3)
    } else {
      val threadId = Thread.currentThread.getId.toInt
      val arr = new Array[Byte](3)

      arr(0) = (threadId & 0xFF).toByte
      arr(1) = (threadId >> 8 & 0xFF).toByte
      arr(2) = (threadId >> 16 & 0xFF).toByte

      arr
    }
  }

  private def md5(bytes: Array[Byte]) = java.security.MessageDigest.getInstance("MD5").digest(bytes)

  /**
   * Constructs a BSON ObjectId element from a hexadecimal String representation.
   * Throws an exception if the given argument is not a valid ObjectID.
   *
   * `parse(str: String): Try[BSONObjectID]` should be considered instead of this method.
   */
  def apply(id: String): BsonObjectId = {
    if (id.length != 24)
      throw new IllegalArgumentException(s"wrong ObjectId: '$id'")
    /** Constructs a BSON ObjectId element from a hexadecimal String representation */
    new BsonObjectId(JsBinary.hex2Bytes(id))
  }

  /** Tries to make a BSON ObjectId element from a hexadecimal String representation. */
  def parse(str: String): Try[BsonObjectId] = Try(apply(str))

  /**
   * Generates a new BSON ObjectID.
   *
   * +------------------------+------------------------+------------------------+------------------------+
   * + timestamp (in seconds) +   machine identifier   +    thread identifier   +        increment       +
   * +        (4 bytes)       +        (3 bytes)       +        (2 bytes)       +        (3 bytes)       +
   * +------------------------+------------------------+------------------------+------------------------+
   *
   * The returned BSONObjectID contains a timestamp set to the current time (in seconds),
   * with the `machine identifier`, `thread identifier` and `increment` properly set.
   */
  def generate: BsonObjectId = fromTime(System.currentTimeMillis, false)

  /**
   * Generates a new BSON ObjectID from the given timestamp in milliseconds.
   *
   * +------------------------+------------------------+------------------------+------------------------+
   * + timestamp (in seconds) +   machine identifier   +    thread identifier   +        increment       +
   * +        (4 bytes)       +        (3 bytes)       +        (2 bytes)       +        (3 bytes)       +
   * +------------------------+------------------------+------------------------+------------------------+
   *
   * The included timestamp is the number of seconds since epoch, so a BSONObjectID time part has only
   * a precision up to the second. To get a reasonably unique ID, you _must_ set `onlyTimestamp` to false.
   *
   * Crafting a BSONObjectID from a timestamp with `fillOnlyTimestamp` set to true is helpful for range queries,
   * eg if you want of find documents an _id field which timestamp part is greater than or lesser than
   * the one of another id.
   *
   * If you do not intend to use the produced BSONObjectID for range queries, then you'd rather use
   * the `generate` method instead.
   *
   * @param fillOnlyTimestamp if true, the returned BSONObjectID will only have the timestamp bytes set; the other will be set to zero.
   */
  def fromTime(timeMillis: Long, fillOnlyTimestamp: Boolean = true): BsonObjectId = {
    // n of seconds since epoch. Big endian
    val timestamp = (timeMillis / 1000).toInt
    val id = new Array[Byte](12)

    id(0) = (timestamp >>> 24).toByte
    id(1) = (timestamp >> 16 & 0xFF).toByte
    id(2) = (timestamp >> 8 & 0xFF).toByte
    id(3) = (timestamp & 0xFF).toByte

    if (!fillOnlyTimestamp) {
      // machine id, 3 first bytes of md5(macadress or hostname)
      id(4) = machineId(0)
      id(5) = machineId(1)
      id(6) = machineId(2)

      // 2 bytes of the pid or thread id. Thread id in our case. Low endian
      val threadId = Thread.currentThread.getId.toInt
      id(7) = (threadId & 0xFF).toByte
      id(8) = (threadId >> 8 & 0xFF).toByte

      // 3 bytes of counter sequence, which start is randomized. Big endian
      val c = counter
      id(9) = (c >> 16 & 0xFF).toByte
      id(10) = (c >> 8 & 0xFF).toByte
      id(11) = (c & 0xFF).toByte
    }

    BsonObjectId(id)
  }
}