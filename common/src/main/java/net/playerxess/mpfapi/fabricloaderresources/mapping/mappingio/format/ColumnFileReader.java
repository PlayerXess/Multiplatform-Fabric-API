/*
 * Copyright (c) 2021 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.tiny.Tiny2Util;

/**
 * Reader for column-based files.
 */
@ApiStatus.Internal
public final class ColumnFileReader implements Closeable {
	public ColumnFileReader(Reader reader, char indentationChar, char columnSeparator) {
		assert indentationChar != '\r';
		assert indentationChar != '\n';
		assert columnSeparator != '\r';
		assert columnSeparator != '\n';

		this.reader = reader;
		this.indentationChar = indentationChar;
		this.columnSeparator = columnSeparator;
	}

	@Override
	public void close() throws IOException {
		reader.close();
	}

	/**
	 * Try to read the current column with specific expected content.
	 *
	 * <p>The reader will point to the next column or end of line if successful, otherwise remains unchanged.
	 *
	 * @param expected Content to expect.
	 * @return {@code true} if the column was read and had the expected content, {@code false} otherwise.
	 */
	public boolean nextCol(String expected) throws IOException {
		return read(false, false, true, expected) != NO_MATCH;
	}

	/**
	 * Read and consume a column without unescaping.
	 *
	 * @return {@code null} if nothing has been read (first char was EOL), otherwise the read string (may be empty).
	 */
	@Nullable
	public String nextCol() throws IOException {
		return nextCol(false);
	}

	/**
	 * Read and consume a column, and unescape it if requested.
	 *
	 * @return {@code null} if nothing has been read (first char was EOL), otherwise the read string (may be empty).
	 */
	@Nullable
	public String nextCol(boolean unescape) throws IOException {
		return read(unescape, true, true, null);
	}

	/**
	 * Read a column without consuming, and unescape if requested.
	 * Since it doesn't consume, it won't (un)mark BOF, EOL or EOF.
	 *
	 * @return {@code null} if nothing has been read (first char was EOL), otherwise the read string (may be empty).
	 */
	@Nullable
	public String peekCol(boolean unescape) throws IOException {
		return read(unescape, false, true, null);
	}

	/**
	 * @param unescape Whether to unescape the read string.
	 * @param consume Whether to advance the bufferPos.
	 * @param stopAtNextCol Whether to only read one column.
	 * @param expected If not {@code null}, the read string must match this exactly, otherwise we early-exit with {@link #NO_MATCH}. Always consumes if matched.
	 *
	 * @return {@code null} if nothing has been read (first char was EOL), otherwise the read string (may be empty).
	 * If {@code expected} is not {@code null}, it will be returned if matched, otherwise {@link #NO_MATCH}.
	 */
	@Nullable
	private String read(boolean unescape, boolean consume, boolean stopAtNextCol, @Nullable String expected) throws IOException {
		if (eol) return expected == null ? null : NO_MATCH;

		int expectedLength = expected != null ? expected.length() : -1;

		// Check if the buffer needs to be filled and if we hit EOF while doing so
		if (expectedLength > 0 && bufferPos + expectedLength >= bufferLimit) {
			if (!fillBuffer(expectedLength, !consume, false)) return NO_MATCH;
		}

		int start;
		int end = this.bufferPos;
		int firstEscaped = -1;
		int contentCharsRead = 0;
		int modifiedBufferPos = -1;
		boolean readAnything = false;
		boolean filled = true;
		boolean isColumnSeparator = false;

		readLoop: for (;;) {
			while (end < bufferLimit) {
				char c = buffer[end];
				isColumnSeparator = (c == columnSeparator);
				readAnything = true;

				if (expected != null) {
					if ((contentCharsRead < expectedLength && c != expected.charAt(contentCharsRead))
							|| contentCharsRead > expectedLength) {
						return NO_MATCH;
					}
				}

				if (c == '\n' || c == '\r' || (isColumnSeparator && stopAtNextCol)) { // stop reading
					start = bufferPos;
					modifiedBufferPos = end;

					if (!isColumnSeparator && (consume || expected != null)) {
						eol = true;
					}

					break readLoop;
				} else if (unescape && c == '\\' && firstEscaped < 0) {
					firstEscaped = bufferPos;
				}

				contentCharsRead++;
				end++;
			}

			// buffer ran out, refill

			int oldStart = bufferPos;
			filled = fillBuffer(end - bufferPos + 1, !consume, consume);
			int posShift = bufferPos - oldStart; // fillBuffer may compact the data, shifting it to the buffer start
			assert posShift <= 0;
			end += posShift;
			if (firstEscaped >= 0) firstEscaped += posShift;

			if (!filled) {
				start = bufferPos;
				break;
			}
		}

		String ret;

		if (expected != null) {
			consume = true;
			ret = expected;
		} else {
			int contentLength = end - start;

			if (contentLength == 0) {
				ret = readAnything ? "" : null;
			} else if (firstEscaped >= 0) {
				ret = Tiny2Util.unescape(String.valueOf(buffer, start, contentLength));
			} else {
				ret = String.valueOf(buffer, start, contentLength);
			}
		}

		if (consume) {
			if (readAnything) bof = false;

			if (modifiedBufferPos != -1) {
				bufferPos = modifiedBufferPos;

				// consume trailing column separator if present
				if (isColumnSeparator && fillBuffer(1, false, false)) {
					bufferPos++;
				}
			}

			if (!filled) eof = eol = true;

			if (eol && !eof) { // manually check for EOF
				int charsToRead = buffer[bufferPos] == '\r' ? 2 : 1; // 2 for \r\n, 1 for just \n

				if (end >= bufferLimit - charsToRead) {
					fillBuffer(charsToRead, false, true);
				}
			}
		}

		return ret;
	}

	/**
	 * Read and consume all columns until EOL, and unescape if requested.
	 *
	 * @return {@code null} if nothing has been read (first char was EOL), otherwise the read string (may be empty).
	 */
	@Nullable
	public String nextCols(boolean unescape) throws IOException {
		return read(unescape, true, false, null);
	}

	/**
	 * Read all columns until EOL without consuming, and unescape if requested.
	 * Since it doesn't consume, it won't (un)mark BOF, EOL or EOF.
	 *
	 * @return {@code null} if nothing has been read (first char was EOL), otherwise the read string (may be empty).
	 */
	@Nullable
	public String peekCols(boolean unescape) throws IOException {
		return read(unescape, false, false, null);
	}

	/**
	 * Read and consume a column and convert it to integer.
	 *
	 * @return -1 if nothing has been read (first char was EOL), otherwise the number present.
	 */
	public int nextIntCol() throws IOException {
		String str = nextCol(false);

		try {
			return str != null ? Integer.parseInt(str) : -1;
		} catch (NumberFormatException e) {
			throw new IOException("invalid number in line "+lineNumber+": "+str);
		}
	}

	/**
	 * Read and consume until the start of the next line is reached, and return whether the
	 * following {@code indent} characters match {@link #indentationChar}.
	 *
	 * <p>Empty lines are skipped if {@code indent} is 0.
	 *
	 * @param indent The number of characters to check for indentation.
	 * @return {@code true} if the next line has the specified indentation or higher, {@code false} otherwise.
	 */
	public boolean nextLine(int indent) throws IOException {
		fillLoop: do {
			while (bufferPos < bufferLimit) {
				char c = buffer[bufferPos];

				if (c == '\n') {
					if (indent == 0) { // skip empty lines if indent is 0
						if (!fillBuffer(2, false, true)) break fillLoop;

						char next = buffer[bufferPos + 1];

						if (next == '\n' || next == '\r') { // 2+ consecutive new lines, consume first nl and retry
							bufferPos++;
							lineNumber++;
							bof = false;
							continue;
						}
					}

					if (!fillBuffer(indent + 1, false, true)) return false;

					for (int i = 1; i <= indent; i++) {
						if (buffer[bufferPos + i] != indentationChar) return false;
					}

					bufferPos += indent + 1;
					lineNumber++;
					bof = false;
					eol = false;

					return true;
				}

				bufferPos++;
				bof = false;
			}
		} while (fillBuffer(1, false, true));

		return false;
	}

	public boolean hasExtraIndents() throws IOException {
		return fillBuffer(1, false, false) && buffer[bufferPos] == indentationChar;
	}

	public int getLineNumber() {
		return lineNumber;
	}

	/**
	 * Whether EOL has been encountered in the current line yet.
	 */
	public boolean isAtEol() {
		return eol;
	}

	public boolean isAtBof() {
		return bof;
	}

	public boolean isAtEof() {
		return eof;
	}

	/**
	 * Marks the present position in the stream. Subsequent calls to
	 * {@link #reset()} will reposition the stream to this point.
	 * In comparison to {@link java.io.Reader#mark(int)} this method stacks,
	 * so don't forget to call {@link #discardMark()} if you don't need the mark anymore.
	 *
	 * @return the mark index (starting at 1)
	 */
	public int mark() {
		if (markIdx == 0 && bufferPos > 0) { // save memory
			int available = bufferLimit - bufferPos;
			System.arraycopy(buffer, bufferPos, buffer, 0, available);
			bufferPos = 0;
			bufferLimit = available;
		}

		if (markIdx == markedBufferPositions.length) {
			markedBufferPositions = Arrays.copyOf(markedBufferPositions, markedBufferPositions.length * 2);
			markedLineNumbers = Arrays.copyOf(markedLineNumbers, markedLineNumbers.length * 2);
			markedBofs = Arrays.copyOf(markedBofs, markedBofs.length * 2);
			markedEols = Arrays.copyOf(markedEols, markedEols.length * 2);
			markedEofs = Arrays.copyOf(markedEofs, markedEofs.length * 2);
		}

		markedBufferPositions[markIdx] = bufferPos;
		markedLineNumbers[markIdx] = lineNumber;
		markedBofs[markIdx] = bof;
		markedEols[markIdx] = eol;
		markedEofs[markIdx] = eof;

		return ++markIdx;
	}

	/**
	 * Discard the last mark.
	 */
	public void discardMark() {
		discardMark(markIdx);
	}

	/**
	 * Discard the mark at specified index and all above, if present.
	 */
	private void discardMark(int index) {
		if (markIdx == 0) throw new IllegalStateException("no mark to discard");
		if (index < 1 || index > markIdx) throw new IllegalStateException("index out of bounds");

		for (int i = markIdx; i >= index; i--) {
			markedBufferPositions[i-1] = 0;
			markedLineNumbers[i-1] = 0;
		}

		markIdx = index - 1;
	}

	/**
	 * Reset to last mark. The marked data isn't discarded, so can be called multiple times.
	 * If you want to reset to an older mark, use {@link #reset(int)}.
	 *
	 * @return The index of the mark that was reset to.
	 */
	public int reset() {
		reset(markIdx);
		return markIdx;
	}

	/**
	 * Reset to the mark with the specified index.
	 * Unless reset to 0, the marked data isn't discarded afterwards,
	 * so can be called multiple times.
	 * Use negative indices to reset to a mark relative to the current one.
	 */
	public void reset(int indexToResetTo) {
		if (markIdx == 0) throw new IllegalStateException("no mark to reset to");
		if (indexToResetTo < -markIdx || indexToResetTo > markIdx) throw new IllegalStateException("index out of bounds");

		if (indexToResetTo < 0) indexToResetTo += markIdx;
		int arrayIdx = indexToResetTo == 0 ? indexToResetTo : indexToResetTo - 1;

		bufferPos = markedBufferPositions[arrayIdx];
		lineNumber = markedLineNumbers[arrayIdx];
		bof = markedBofs[arrayIdx];
		eol = markedEols[arrayIdx];
		eof = markedEofs[arrayIdx];

		if (indexToResetTo == 0) discardMark(1);
		markIdx = indexToResetTo;
	}

	private boolean fillBuffer(int count, boolean preventCompaction, boolean markEof) throws IOException {
		int available = bufferLimit - bufferPos;
		int req = count - available;
		if (req <= 0) return true;

		if (bufferPos + count > buffer.length) { // not enough remaining buffer space
			if (markIdx > 0 || preventCompaction) { // can't compact -> grow
				buffer = Arrays.copyOf(buffer, Math.max(bufferPos + count, buffer.length * 2));
			} else { // compact and grow as needed
				if (count > buffer.length) { // too small for compacting to suffice -> grow and compact
					char[] newBuffer = new char[Math.max(count, buffer.length * 2)];
					System.arraycopy(buffer, bufferPos, newBuffer, 0, available);
					buffer = newBuffer;
				} else { // compact
					System.arraycopy(buffer, bufferPos, buffer, 0, available);
				}

				bufferPos = 0;
				bufferLimit = available;
			}
		}

		int reqLimit = bufferLimit + req;

		do {
			int read = reader.read(buffer, bufferLimit, buffer.length - bufferLimit);

			if (read < 0) {
				if (markEof) eof = eol = true;
				return false;
			}

			bufferLimit += read;
		} while (bufferLimit < reqLimit);

		return true;
	}

	private static final String NO_MATCH = new String();
	private final Reader reader;
	private final char indentationChar;
	private final char columnSeparator;
	private char[] buffer = new char[4096 * 4];
	private int bufferPos;
	private int bufferLimit;
	private int lineNumber = 1;
	private boolean bof = true;
	private boolean eol; // tracks whether the last column has been read, otherwise ambiguous if the last col is empty
	private boolean eof;
	private int markIdx = 0; // 0 means no mark
	private int[] markedBufferPositions = new int[3];
	private int[] markedLineNumbers = new int[3];
	private boolean[] markedBofs = new boolean[3];
	private boolean[] markedEols = new boolean[3];
	private boolean[] markedEofs = new boolean[3];
}
