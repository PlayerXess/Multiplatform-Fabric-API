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

package net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jetbrains.annotations.Nullable;

import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.MappingFormat;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.enigma.EnigmaDirWriter;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.enigma.EnigmaFileWriter;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.intellij.MigrationMapFileWriter;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.jobf.JobfFileWriter;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.proguard.ProGuardFileWriter;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.simple.RecafSimpleFileWriter;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.srg.CsrgFileWriter;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.srg.JamFileWriter;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.srg.SrgFileWriter;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.srg.TsrgFileWriter;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.tiny.Tiny1FileWriter;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.tiny.Tiny2FileWriter;

public interface MappingWriter extends Closeable, MappingVisitor {
	@Nullable
	static MappingWriter create(Path file, MappingFormat format) throws IOException {
		if (format.hasSingleFile()) {
			return create(Files.newBufferedWriter(file), format);
		} else {
			switch (format) {
			case ENIGMA_DIR: return new EnigmaDirWriter(file, true);
			default: return null;
			}
		}
	}

	@Nullable
	static MappingWriter create(Writer writer, MappingFormat format) throws IOException {
		if (!format.hasSingleFile()) throw new IllegalArgumentException("format "+format+" is not applicable to a single writer");

		switch (format) {
		case TINY_FILE: return new Tiny1FileWriter(writer);
		case TINY_2_FILE: return new Tiny2FileWriter(writer, false);
		case ENIGMA_FILE: return new EnigmaFileWriter(writer);
		case SRG_FILE: return new SrgFileWriter(writer, false);
		case XSRG_FILE: return new SrgFileWriter(writer, true);
		case JAM_FILE: return new JamFileWriter(writer);
		case CSRG_FILE: return new CsrgFileWriter(writer);
		case TSRG_FILE: return new TsrgFileWriter(writer, false);
		case TSRG_2_FILE: return new TsrgFileWriter(writer, true);
		case PROGUARD_FILE: return new ProGuardFileWriter(writer);
		case INTELLIJ_MIGRATION_MAP_FILE: return new MigrationMapFileWriter(writer);
		case RECAF_SIMPLE_FILE: return new RecafSimpleFileWriter(writer);
		case JOBF_FILE: return new JobfFileWriter(writer);
		default: return null;
		}
	}

	@Override
	default boolean visitEnd() throws IOException {
		close();
		return true;
	}
}
