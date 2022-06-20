package be.dnsbelgium.mercator.tls.domain.ssl2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.slf4j.LoggerFactory.getLogger;

public class ClientHelloTest {

  ClientHelloEncoder encoder = new ClientHelloEncoder();
  ClientHelloDecoder decoder = new ClientHelloDecoder();

  private final List<SSL2CipherSuite> ciphers = List.of(
      SSL2CipherSuite.SSL_CK_DES_64_CBC_WITH_MD5,
      SSL2CipherSuite.SSL_CK_RC2_128_CBC_WITH_MD5,
      SSL2CipherSuite.SSL_CK_IDEA_128_CBC_WITH_MD5
  );

  private static final Logger logger = getLogger(ClientHelloTest.class);

  @Test
  public void encodeDecode_With_SessionId() throws Exception {
    byte[] sessionId = RandomUtils.nextBytes(32);
    encodeDecode(sessionId);
  }

  @Test
  public void noSessionId() throws Exception {
    byte[] sessionId = new byte[0];
    encodeDecode(sessionId);
  }

  public void encodeDecode(byte[] sessionId) {
    byte[] challenge = RandomUtils.nextBytes(32);
    ClientHello clientHello = new ClientHello(2, ciphers, sessionId, challenge);

    int expectedLength = 9 + sessionId.length + challenge.length + 3 * ciphers.size();
    assertThat(encoder.bytesNeeded(clientHello)).isEqualTo(expectedLength);

    logger.info("clientHello = {}", clientHello);
    ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(100);
    encoder.encode(null, clientHello, buffer);
    logger.info("After encoding: buffer = {}", buffer);
    logger.info("After encoding: buffer.readableBytes = {}", buffer.readableBytes());
    List<Object> output = new ArrayList<>();
    decoder.decode(null, buffer, output);
    logger.info("output of decoder: {}", output);
    assertThat(output).hasSize(1);
    assertThat(output.get(0)).isEqualTo(clientHello);
  }

}
