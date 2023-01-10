package ch.uzh.ifi.seal.changedistiller.ast;

/*
 * #%L ChangeDistiller %% Copyright (C) 2011 - 2013 Software Architecture and
 * Evolution Lab, Department of Informatics, UZH %% Licensed under the Apache
 * License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License. #L%
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Utility class to handle {@link File}s.
 *
 * @author Beat Fluri
 * @author Kai Presler-Marshall
 */
public final class FileUtils {

    private FileUtils () {
    }

    /**
     * Returns the content of the {@link File} as {@link String}.
     *
     * @param file
     *            to read the content from
     * @return the content of the file
     */
    public static String getContent ( final File file ) {

        try {
            return Files.readString( file.toPath() );

        }
        catch ( final IOException ioe ) {
            throw new RuntimeException( ioe );
        }

    }

}
