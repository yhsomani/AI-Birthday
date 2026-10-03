import { createHmac } from 'crypto';
import { addField } from '../../src/domain/opaque';

describe('opaque domain', () => {
  it('should correctly update hmac with length and value', () => {
    // Create a mock hmac to spy on update calls
    const mockHmac = {
      update: jest.fn(),
    } as unknown as ReturnType<typeof createHmac>;

    const value = new Uint8Array([1, 2, 3, 4, 5]);

    addField(mockHmac, value);

    expect(mockHmac.update).toHaveBeenCalledTimes(2);

    // First call should be with the length (5) as a 4-byte buffer
    const lengthBuffer = Buffer.alloc(4);
    lengthBuffer.writeUInt32BE(5);
    expect(mockHmac.update).toHaveBeenNthCalledWith(1, lengthBuffer);

    // Second call should be with the actual value
    expect(mockHmac.update).toHaveBeenNthCalledWith(2, value);
  });
});
