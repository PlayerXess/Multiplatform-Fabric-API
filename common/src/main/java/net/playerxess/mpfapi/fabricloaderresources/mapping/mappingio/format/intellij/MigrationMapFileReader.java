/*
 * Copyright (c) 2024 FabricMC
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

package net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.intellij;

import java.io.BufferedReader;
import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappedElementKind;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingFlag;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingUtil;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingVisitor;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.MappingFormat;

/**
 * {@linkplain MappingFormat#INTELLIJ_MIGRATION_MAP_FILE IntelliJ migration map} reader.
 *
 * <p>Crashes if a second visit pass is requested without
 * {@link MappingFlag#NEEDS_MULTIPLE_PASSES} having been passed beforehand.
 */
public final class MigrationMapFileReader {
	private MigrationMapFileReader() {
	}

	public static void read(Reader reader, MappingVisitor visitor) throws IOException {
		read(reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, visitor);
	}

	public static void read(Reader reader, String sourceNs, String targetNs, MappingVisitor visitor) throws IOException {
		BufferedReader br = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);

		read(br, sourceNs, targetNs, visitor);
	}

	private static void read(BufferedReader reader, String sourceNs, String targetNs, MappingVisitor visitor) throws IOException {
		try {
			read0(reader, sourceNs, targetNs, visitor);
		} catch (XMLStreamException e) {
			throw new IOException(e);
		}
	}

	private static void read0(BufferedReader reader, String sourceNs, String targetNs, MappingVisitor visitor) throws IOException, XMLStreamException {
		CharArrayReader parentReader = null;

		if (visitor.getFlags().contains(MappingFlag.NEEDS_MULTIPLE_PASSES)) {
			char[] buffer = new char[100_000];
			int pos = 0;
			int len;

			while ((len = reader.read(buffer, pos, buffer.length - pos)) >= 0) {
				pos += len;

				if (pos == buffer.length) buffer = Arrays.copyOf(buffer, buffer.length * 2);
			}

			parentReader = new CharArrayReader(buffer, 0, pos);
			reader = new CustomBufferedReader(parentReader);
		}

		XMLInputFactory factory = XMLInputFactory.newInstance();

		for (;;) {
			XMLStreamReader xmlReader = factory.createXMLStreamReader(reader);
			boolean visitHeader;

			if (visitHeader = visitor.visitHeader()) {
				visitor.visitNamespaces(sourceNs, Collections.singletonList(targetNs));
			}

			if (visitor.visitContent()) {
				int depth = 0;

				while (xmlReader.hasNext()) {
					int event = xmlReader.next();

					switch (event) {
					case XMLStreamConstants.START_ELEMENT:
						String name = xmlReader.getLocalName();

						if (depth != (name.equals("migrationMap") ? 0 : 1)) {
							throw new IOException("unexpected depth at line "+xmlReader.getLocation().getLineNumber());
						}

						depth++;

						switch (name) {
						case "name":
						case "order":
						case "description":
							if (visitHeader) {
								String value = xmlReader.getAttributeValue(null, "value");

								if (name.equals("order")) name = MigrationMapConstants.ORDER_KEY;
								if (name.equals("name") && value.equals(MigrationMapConstants.MISSING_NAME)) break;

								visitor.visitMetadata(name, value);
							}

							break;
						case "entry":
							String type = xmlReader.getAttributeValue(null, "type");

							if (type == null || type.isEmpty()) throw new IOException("missing/empty type attribute at line "+xmlReader.getLocation().getLineNumber());
							if (type.equals("package")) continue; // TODO: support packages
							if (!type.equals("class")) throw new IOException("unexpected entry type "+type+" at line "+xmlReader.getLocation().getLineNumber());

							String srcName = xmlReader.getAttributeValue(null, "oldName");
							String dstName = xmlReader.getAttributeValue(null, "newName");
							// String recursive = xmlReader.getAttributeValue(null, "recursive"); // only used for packages

							if (srcName == null || srcName.isEmpty()) throw new IOException("missing/empty oldName attribute at line "+xmlReader.getLocation().getLineNumber());
							if (dstName == null || dstName.isEmpty()) throw new IOException("missing/empty newName attribute at line "+xmlReader.getLocation().getLineNumber());

							srcName = srcName.replace('.', '/');
							dstName = dstName.replace('.', '/');

							if (visitor.visitClass(srcName)) {
								visitor.visitDstName(MappedElementKind.CLASS, 0, dstName);
								visitor.visitElementContent(MappedElementKind.CLASS);
							}

							break;
						}

						break;
					case XMLStreamConstants.END_ELEMENT:
						depth--;
						break;
					}
				}
			}

			if (visitor.visitEnd()) {
				if (parentReader != null) {
					((CustomBufferedReader) reader).forceClose();
				}

				break;
			}

			if (parentReader == null) {
				throw new IllegalStateException("repeated visitation requested without NEEDS_MULTIPLE_PASSES");
			} else {
				parentReader.reset();
				reader = new CustomBufferedReader(parentReader);
			}
		}
	}

	private static class CustomBufferedReader extends BufferedReader {
		private CustomBufferedReader(Reader in) {
			super(in);
		}

		public void forceClose() throws IOException {
			super.close();
		}

		@Override
		public void close() throws IOException {
		}
	}
}
