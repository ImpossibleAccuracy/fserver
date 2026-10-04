//! Big-endian primitives everything on the wire is built from:
//! `bytes` = `i32 length` + raw, `string` = UTF-8 as `bytes`.

use crate::NetError;

/// Minimal big-endian writer. Everything `fx-net` puts on the wire is built with this.
#[derive(Debug, Default, Clone)]
pub struct ByteWriter {
    buf: Vec<u8>,
}

impl ByteWriter {
    /// Empty writer.
    pub fn new() -> Self {
        Self::default()
    }

    /// Empty writer with room for `capacity` bytes before it reallocates.
    pub fn with_capacity(capacity: usize) -> Self {
        Self {
            buf: Vec::with_capacity(capacity),
        }
    }

    /// One unsigned byte.
    pub fn u8(&mut self, value: u8) -> &mut Self {
        self.buf.push(value);
        self
    }

    /// Two unsigned bytes.
    pub fn u16(&mut self, value: u16) -> &mut Self {
        self.buf.extend_from_slice(&value.to_be_bytes());
        self
    }

    /// Four unsigned bytes.
    pub fn u32(&mut self, value: u32) -> &mut Self {
        self.buf.extend_from_slice(&value.to_be_bytes());
        self
    }

    /// Big-endian `i32`.
    pub fn i32(&mut self, value: i32) -> &mut Self {
        self.buf.extend_from_slice(&value.to_be_bytes());
        self
    }

    /// Big-endian `i64`.
    pub fn i64(&mut self, value: i64) -> &mut Self {
        self.buf.extend_from_slice(&value.to_be_bytes());
        self
    }

    /// `1` or `0`.
    pub fn bool(&mut self, value: bool) -> &mut Self {
        self.u8(u8::from(value))
    }

    /// `i32` length prefix, then the bytes.
    ///
    /// # Panics
    /// If `value` is longer than `i32::MAX` - far above any frame limit, so a caller bug.
    pub fn bytes(&mut self, value: &[u8]) -> &mut Self {
        let len = i32::try_from(value.len())
            .unwrap_or_else(|_| panic!("{} bytes do not fit an i32 length prefix", value.len()));
        self.i32(len).raw(value)
    }

    /// UTF-8 as [`bytes`](Self::bytes).
    pub fn string(&mut self, value: &str) -> &mut Self {
        self.bytes(value.as_bytes())
    }

    /// The bytes as they are, without a length prefix.
    pub fn raw(&mut self, value: &[u8]) -> &mut Self {
        self.buf.extend_from_slice(value);
        self
    }

    /// Bytes written so far.
    pub fn as_bytes(&self) -> &[u8] {
        &self.buf
    }

    /// Finishes writing and hands over the buffer.
    pub fn into_bytes(self) -> Vec<u8> {
        self.buf
    }
}

/// Matching reader. Every malformed input is a [`NetError::Protocol`], never a panic.
#[derive(Debug, Clone)]
pub struct ByteReader<'a> {
    buf: &'a [u8],
}

impl<'a> ByteReader<'a> {
    /// Reader over `source`, starting at its first byte.
    pub fn new(source: &'a [u8]) -> Self {
        Self { buf: source }
    }

    /// Bytes not read yet.
    pub fn remaining(&self) -> usize {
        self.buf.len()
    }

    /// One unsigned byte.
    pub fn u8(&mut self) -> Result<u8, NetError> {
        self.array::<1>().map(|[b]| b)
    }

    /// Two unsigned bytes.
    pub fn u16(&mut self) -> Result<u16, NetError> {
        self.array().map(u16::from_be_bytes)
    }

    /// Four unsigned bytes.
    pub fn u32(&mut self) -> Result<u32, NetError> {
        self.array().map(u32::from_be_bytes)
    }

    /// Big-endian `i32`.
    pub fn i32(&mut self) -> Result<i32, NetError> {
        self.array().map(i32::from_be_bytes)
    }

    /// Big-endian `i64`.
    pub fn i64(&mut self) -> Result<i64, NetError> {
        self.array().map(i64::from_be_bytes)
    }

    /// Any non-zero byte is `true`.
    pub fn bool(&mut self) -> Result<bool, NetError> {
        self.u8().map(|b| b != 0)
    }

    /// `i32` length prefix, then that many bytes. A negative length or one past the end of
    /// the input is a protocol error.
    pub fn bytes(&mut self) -> Result<&'a [u8], NetError> {
        let declared = self.i32()?;
        let len = usize::try_from(declared)
            .ok()
            .filter(|&len| len <= self.buf.len())
            .ok_or_else(|| {
                NetError::protocol(format!(
                    "declared length {declared} does not fit in {} bytes",
                    self.buf.len()
                ))
            })?;
        let (value, rest) = self.buf.split_at(len);
        self.buf = rest;
        Ok(value)
    }

    /// UTF-8 as [`bytes`](Self::bytes). Invalid UTF-8 is a protocol error.
    pub fn string(&mut self) -> Result<&'a str, NetError> {
        std::str::from_utf8(self.bytes()?).map_err(|e| NetError::Protocol {
            reason: "string is not UTF-8".to_owned(),
            source: Some(Box::new(e)),
        })
    }

    /// Everything not read yet; leaves the reader empty.
    pub fn rest(&mut self) -> &'a [u8] {
        std::mem::take(&mut self.buf)
    }

    fn array<const N: usize>(&mut self) -> Result<[u8; N], NetError> {
        let (head, rest) = self
            .buf
            .split_first_chunk::<N>()
            .ok_or_else(|| NetError::protocol("frame truncated"))?;
        self.buf = rest;
        Ok(*head)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn writes_big_endian_with_i32_length_prefixes() {
        let mut w = ByteWriter::new();
        w.u8(0xAB)
            .i32(0x0102_0304)
            .i64(-2)
            .bool(true)
            .bool(false)
            .bytes(&[9, 8])
            .string("é")
            .raw(&[7]);

        assert_eq!(
            w.into_bytes(),
            [
                0xAB, // u8
                0x01, 0x02, 0x03, 0x04, // i32
                0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFE, // i64 -2
                0x01, 0x00, // bools
                0x00, 0x00, 0x00, 0x02, 9, 8, // bytes
                0x00, 0x00, 0x00, 0x02, 0xC3, 0xA9, // "é"
                7,    // raw
            ]
        );
    }

    #[test]
    fn reads_back_what_was_written() {
        let mut w = ByteWriter::with_capacity(1);
        w.u8(200)
            .i32(i32::MIN)
            .i64(i64::MAX)
            .bool(true)
            .bytes(&[])
            .string("peer")
            .raw(&[1, 2, 3]);
        let frame = w.into_bytes();

        let mut r = ByteReader::new(&frame);
        assert_eq!(r.u8().unwrap(), 200);
        assert_eq!(r.i32().unwrap(), i32::MIN);
        assert_eq!(r.i64().unwrap(), i64::MAX);
        assert!(r.bool().unwrap());
        assert_eq!(r.bytes().unwrap(), &[] as &[u8]);
        assert_eq!(r.string().unwrap(), "peer");
        assert_eq!(r.remaining(), 3);
        assert_eq!(r.rest(), &[1, 2, 3]);
        assert_eq!(r.remaining(), 0);
    }

    #[test]
    fn any_non_zero_byte_is_true() {
        assert!(ByteReader::new(&[0x7F]).bool().unwrap());
    }

    #[test]
    fn truncated_frame_is_a_protocol_error() {
        let mut r = ByteReader::new(&[0, 0, 1]);
        assert!(
            matches!(r.i32(), Err(NetError::Protocol { reason, .. }) if reason == "frame truncated")
        );
        assert!(matches!(
            ByteReader::new(&[]).u8(),
            Err(NetError::Protocol { .. })
        ));
    }

    #[test]
    fn length_past_the_end_is_a_protocol_error() {
        let mut r = ByteReader::new(&[0, 0, 0, 3, 1, 2]);
        assert!(matches!(
            r.bytes(),
            Err(NetError::Protocol { reason, .. }) if reason == "declared length 3 does not fit in 2 bytes"
        ));
    }

    #[test]
    fn negative_length_is_a_protocol_error() {
        let mut r = ByteReader::new(&[0xFF, 0xFF, 0xFF, 0xFF]);
        assert!(matches!(r.bytes(), Err(NetError::Protocol { .. })));
    }

    #[test]
    fn invalid_utf8_is_a_protocol_error() {
        let mut r = ByteReader::new(&[0, 0, 0, 1, 0xFF]);
        assert!(matches!(r.string(), Err(NetError::Protocol { .. })));
    }
}
