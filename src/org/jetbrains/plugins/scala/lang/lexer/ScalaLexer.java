/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerPosition;
import com.intellij.lexer.LexerState;
import com.intellij.lexer.XmlLexer;
import com.intellij.psi.tree.IElementType;
import static com.intellij.psi.xml.XmlTokenType.*;
import com.intellij.util.text.CharArrayCharSequence;
import gnu.trove.TIntStack;
import org.jetbrains.annotations.Nullable;
import static org.jetbrains.plugins.scala.lang.lexer.ScalaLexer.TAG_STATE.NONEMPTY;
import static org.jetbrains.plugins.scala.lang.lexer.ScalaLexer.TAG_STATE.UNDEFINED;
import static org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx.*;

import java.util.Stack;

/**
 * @author ilyas
 */
public class ScalaLexer implements Lexer {


  private final Lexer myScalaPlainLexer = new ScalaPlainLexer();
  private final Lexer myXmlLexer = new XmlLexer();

  private Lexer myCurrentLexer;

  private static final int MASK = 0x3F;
  private static final int XML_SHIFT = 6;
  TIntStack myBraceStack = new TIntStack();
  Stack<Stack<MyOpenXmlTag>> myLayeredTagStack = new Stack<Stack<MyOpenXmlTag>>();


  private int myBufferEnd;
  private CharSequence myBuffer;
  private int myXmlState;
  private IElementType myCurrentType;
  public final String XML_BEGIN_PATTERN = "\\s*<\\w+.*";

  public ScalaLexer() {
    myCurrentLexer = myScalaPlainLexer;
  }

  @Deprecated
  public void start(char[] buffer) {
    start(buffer, 0, buffer.length);
  }

  @Deprecated
  public void start(char[] buffer, int startOffset, int endOffset) {
    start(buffer, startOffset, endOffset, 0);
  }

  @Deprecated
  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    start(new CharArrayCharSequence(buffer), startOffset, endOffset, initialState);
  }

  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myXmlState = (initialState >> XML_SHIFT) & MASK;
    if (myCurrentLexer == null) myCurrentLexer = myScalaPlainLexer;
    if (myCurrentLexer instanceof XmlLexer) {
      myCurrentLexer.start(buffer, startOffset, endOffset, myXmlState);
    } else {
      myCurrentLexer.start(buffer, startOffset, endOffset, initialState & MASK);
    }
    myBuffer = buffer;
    myBufferEnd = endOffset;
  }

  public int getState() {
    int scalaState = myScalaPlainLexer.getState();
    int xmlState = myXmlLexer.getState();
    return scalaState | (xmlState << XML_SHIFT);
  }

  @Nullable
  public IElementType getTokenType() {
    return myCurrentType == null ? myCurrentLexer.getTokenType() : myCurrentType;
  }

  private IElementType updateLexers() {
    IElementType type = myCurrentLexer.getTokenType();
    int start = myCurrentLexer.getTokenStart();
    String tokenText = myCurrentLexer.getBufferSequence().subSequence(start, myCurrentLexer.getTokenEnd()).toString();

    if (type == SCALA_XML_CONTENT_START) {
      myCurrentLexer = myXmlLexer;
      myCurrentLexer.start(getBufferSequence(), start, myBufferEnd, myXmlState);
      myLayeredTagStack.push(new Stack<MyOpenXmlTag>());
      myLayeredTagStack.peek().push(new MyOpenXmlTag());
      return myCurrentLexer.getTokenType();
    } else if ((type == XML_ATTRIBUTE_VALUE_TOKEN || type == XML_DATA_CHARACTERS) &&
        tokenText.startsWith("{")) {
      myXmlState = myCurrentLexer.getState();
      (myCurrentLexer = myScalaPlainLexer).start(getBufferSequence(), start, myBufferEnd, 0);
      myBraceStack.push(1);
      return SCALA_IN_XML_INJECTION_START;
    } else if (type == ScalaTokenTypes.tRBRACE && myBraceStack.size() > 0) {
      int currentLayer = myBraceStack.pop();
      if (currentLayer == 1) {
        (myCurrentLexer = myXmlLexer).start(getBufferSequence(), start, myBufferEnd, myXmlState);
        return SCALA_IN_XML_INJECTION_END;
      } else {
        myBraceStack.push(--currentLayer);
      }
    } else if (type == ScalaTokenTypes.tLBRACE && myBraceStack.size() > 0) {
      int currentLayer = myBraceStack.pop();
      myBraceStack.push(++currentLayer);
    } else

      // XML injections
      if (XML_START_TAG_START == type && !myLayeredTagStack.isEmpty()) {

        myLayeredTagStack.peek().push(new MyOpenXmlTag());

      } else if (XML_EMPTY_ELEMENT_END == type && !myLayeredTagStack.isEmpty() &&
          !myLayeredTagStack.peek().isEmpty() && myLayeredTagStack.peek().peek().state == UNDEFINED) {

        myLayeredTagStack.peek().pop();
        if (myLayeredTagStack.peek().isEmpty() && checkNotNextXmlBegin(myCurrentLexer)) {
          myLayeredTagStack.pop();
          (myCurrentLexer = myScalaPlainLexer).start(getBufferSequence(), start, myBufferEnd, 0);
        }
      } else if (XML_TAG_END == type && !myLayeredTagStack.isEmpty() && !myLayeredTagStack.peek().isEmpty()) {

        MyOpenXmlTag tag = myLayeredTagStack.peek().peek();
        if (tag.state == UNDEFINED) {
          tag.state = NONEMPTY;
        } else if (tag.state == NONEMPTY) {
          myLayeredTagStack.peek().pop();
        }
        if (myLayeredTagStack.peek().isEmpty()) {
          myLayeredTagStack.pop();
          (myCurrentLexer = myScalaPlainLexer).start(getBufferSequence(), start, myBufferEnd, 0);
        }

      }
    return type;
  }

  private boolean checkNotNextXmlBegin(Lexer lexer) {
    String text = lexer.getBufferSequence().toString();
    int beginIndex = lexer.getTokenEnd();
    if (beginIndex < text.length()) {
      text = text.substring(beginIndex);
      return !text.matches(XML_BEGIN_PATTERN);
    }
    return true;
  }

  public int getTokenStart() {
    return myCurrentLexer.getTokenStart();
  }

  public int getTokenEnd() {
    return myCurrentLexer.getTokenEnd();
  }

  public void advance() {
    myCurrentLexer.advance();
    myCurrentType = updateLexers();
  }

  public LexerPosition getCurrentPosition() {
    return new MyPosition(myCurrentLexer.getTokenStart(),
        new MyState(
            myXmlState,
            0,
            myBraceStack,
            myCurrentLexer,
            myLayeredTagStack));
  }

  public void restore(LexerPosition position) {
    MyPosition pos = (MyPosition) position;
    myBraceStack = pos.state.braceStack;
    myCurrentLexer = pos.state.currentLexer;
    int start = pos.start;
    myLayeredTagStack = pos.state.tagStack;
    myCurrentLexer.start(myCurrentLexer.getBufferSequence(), start, myBufferEnd,
        myCurrentLexer instanceof XmlLexer ? pos.state.xmlState : 0);
  }

  @Deprecated
  public char[] getBuffer() {
    return myCurrentLexer.getBuffer();
  }

  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  public int getBufferEnd() {
    return myBufferEnd;
  }


  private static class MyState implements LexerState {

    public TIntStack braceStack;
    public Stack<Stack<MyOpenXmlTag>> tagStack;
    public Lexer currentLexer;
    public int xmlState;
    public int scalaState;


    public MyState(final int xmlState, final int scalaState, TIntStack braceStack, Lexer lexer, Stack<Stack<MyOpenXmlTag>> tagStack) {
      this.braceStack = braceStack;
      this.tagStack = tagStack;
      this.currentLexer = lexer;
      this.xmlState = xmlState;
      this.scalaState = scalaState;
    }

    public short intern() {
      return 0;
    }
  }

  private static class MyPosition implements LexerPosition {
    public int start;
    public MyState state;

    public MyPosition(final int start, final MyState state) {
      this.start = start;
      this.state = state;
    }

    public int getOffset() {
      return start;
    }

    public LexerState getState() {
      return state;
    }
  }

  protected static enum TAG_STATE {
    UNDEFINED, EMPTY, NONEMPTY
  }

  private static class MyOpenXmlTag {
    public TAG_STATE state = UNDEFINED;
  }


}
