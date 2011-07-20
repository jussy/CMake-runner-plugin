/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.makerunner.agent.output;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.SingleValueConverter;
import com.thoughtworks.xstream.converters.enums.EnumSingleValueConverter;
import jetbrains.buildServer.messages.XStreamHolder;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.xstream.XStreamWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * {@code RegexParser} is an parser designed to use regular expressions in order
 * to parse build output to change output type for Errors, Warnings or some other using {@link RegexPattern}
 *
 * @author Vladislav.Rassokhin
 */
public class RegexParser {
  private String myId;
  private String myName;
  private final List<RegexPattern> myPatterns = new ArrayList<RegexPattern>();

  /**
   * Default constructor will initialize the parser with the name of the class
   * using reflection mechanism.
   */
  public RegexParser() {
    myName = this.getClass().getSimpleName();
    myId = this.getClass().getCanonicalName();
  }

  /**
   * Constructor to initialize ID and name of the error parser.
   *
   * @param id   - ID of the parser.
   * @param name - name of the parser.
   */
  public RegexParser(final String id, final String name) {
    myName = name;
    myId = id;
  }

  public void setId(final String id) {
    myId = id;
  }

  public void setName(final String name) {
    myName = name;
  }

  /**
   * Add new {@link RegexPattern}.
   *
   * @param pattern - new pattern
   */
  public void addPattern(final RegexPattern pattern) {
    myPatterns.add(pattern);
  }

  /**
   * Remove pattern from processing.
   *
   * @param pattern - pattern to remove
   */
  public void removePattern(final RegexPattern pattern) {
    myPatterns.remove(pattern);
  }

  /**
   * Remove all patterns.
   */
  public void clearPatterns() {
    myPatterns.clear();
  }

  /**
   * Method toString() for debugging purposes.
   */
  @Override
  public String toString() {
    return "id=" + myId + ", name=" + myName;
  }

  /**
   * @return id of parser
   */
  public String getId() {
    return myId;
  }

  /**
   * @return name of parser
   */
  public String getName() {
    return myName;
  }

  /**
   * @return list of patterns of this parser.
   */
  public List<RegexPattern> getPatterns() {
    return myPatterns;
  }


  /**
   * Parse a line of build output.
   *
   * @param line    - line of the input
   * @param manager - parsing manager
   * @return true if parser recognized and accepted line, false otherwise
   */
  public boolean processLine(final String line, final Manager manager) {
    for (final RegexPattern pattern : myPatterns) {
      try {
        if (pattern.processLine(line, manager)) {
          return true;
        }
      } catch (Exception e) {
        manager.parsingError("Error parsing line [" + line + "]");
      }
    }
    return false;
  }

  @NotNull
  public String serialize() {
    return XStreamWrapper.serializeObject(this, createXStreamHolder());
  }

  @Nullable
  public static RegexParser deserialize(@NotNull final InputStream serialized) throws IOException {
    final StringBuilder sb = new StringBuilder();
    StringUtil.processLines(serialized, new StringUtil.LineProcessor() {
      public boolean processLine(final String line) {
        sb.append(line);
        return false;
      }
    });
    return deserialize(sb.toString());
  }


  @Nullable
  public static RegexParser deserialize(@NotNull final String xml) {
    // TODO: (in TC 7.0) use XStreamWrapper.deserializeObject(String, XStreamHolder, ClassLoader)
    // Because current  XStreamWrapper.deserializeObject(String, XStreamHolder) will be deprecated.
    final XStreamHolder holder = createXStreamHolder();
    if (xml.length() > 0) {
      final XStream xStream = holder.getXStream(RegexParser.class.getClassLoader());
      try {
        return (RegexParser) xStream.fromXML(xml);
      } finally {
        holder.releaseXStream(xStream);
      }
    } else {
      return null;
    }
  }

  private static class PatternConverter implements SingleValueConverter {
    public String toString(final Object o) {
      return ((Pattern) o).pattern();
    }

    public Object fromString(final String s) {
      return Pattern.compile(s);
    }

    public boolean canConvert(final Class aClass) {
      return Pattern.class.equals(aClass);
    }
  }

  @NotNull
  private static XStreamHolder createXStreamHolder() {
    return new XStreamHolder() {
      @Override
      protected void configureXStream(final XStream xStream) {
        xStream.alias("parser", RegexParser.class);

        xStream.aliasField("id", RegexParser.class, "myId");
        xStream.aliasField("name", RegexParser.class, "myName");
        xStream.useAttributeFor(RegexParser.class, "myId");
        xStream.useAttributeFor(RegexParser.class, "myName");
        xStream.addImplicitCollection(RegexParser.class, "myPatterns");

        xStream.alias("pattern", RegexPattern.class);
        xStream.aliasField("regex", RegexPattern.class, "myPattern");
        xStream.aliasField("severity", RegexPattern.class, "mySeverity");
        xStream.aliasField("output-expr", RegexPattern.class, "myDescriptionExpression");
        xStream.aliasField("eat-line", RegexPattern.class, "myEatLine");
        xStream.useAttributeFor(RegexPattern.class, "myPattern");
        xStream.useAttributeFor(RegexPattern.class, "mySeverity");
        xStream.useAttributeFor(RegexPattern.class, "myDescriptionExpression");
        xStream.useAttributeFor(RegexPattern.class, "myEatLine");
        xStream.registerConverter(new EnumSingleValueConverter(Severity.class));
        xStream.registerConverter(new PatternConverter());
      }
    };
  }
}
