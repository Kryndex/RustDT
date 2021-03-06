/*******************************************************************************
 * Copyright (c) 2016 Bruno Medeiros and other Contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Bruno Medeiros - initial API and implementation
 *******************************************************************************/
package com.github.rustdt.tooling;

import static melnorme.utilbox.core.Assert.AssertNamespace.assertFail;
import static melnorme.utilbox.core.Assert.AssertNamespace.assertTrue;
import static melnorme.utilbox.status.Severity.ERROR;
import static melnorme.utilbox.status.Severity.INFO;

import java.io.StringReader;

import org.junit.Test;

import melnorme.lang.tooling.common.SourceLineColumnRange;
import melnorme.lang.tooling.common.ToolSourceMessage;
import melnorme.utilbox.collections.ArrayList2;
import melnorme.utilbox.collections.Indexable;
import melnorme.utilbox.core.CommonException;

public class RustMessageParserTest extends CommonRustMessageParserTest {
	
	// This is "<std macros>" in original JSON, 
	// but we convert to "", due to a minor API limitation
	public static final String STD_MACROS_PATH = "";
	
	public static Indexable<ToolSourceMessage> DONT_CHECK_ToolMessages = null;
	
	public static final String CANNOT_BORROW_MUT_MORE_THAN = 
		"cannot borrow `xpto` as mutable more than once at a time";
	
	public static final SourceLineColumnRange PARENT_MSG_RANGE = range(1, 1, 1, 1); // Maybe should be -1 ?

	public static final RustMainMessage MSG_Simple = new RustMainMessage(
		new ToolSourceMessage(path(""), PARENT_MSG_RANGE, ERROR, "unresolved name `xpto`"),
		"E0425",
		null, 
		list(
			new RustSubMessage(msg("src/main.rs", 8, 19, 8, 23, ERROR, "unresolved name"))
		)
	);
	public static final RustMainMessage MSG_CannotReborrow = new RustMainMessage(
		new ToolSourceMessage(path(""), PARENT_MSG_RANGE, ERROR, CANNOT_BORROW_MUT_MORE_THAN),
		"E0499",
		null, 
		list(
			new RustSubMessage(msg("src/main.rs", 6,19,6,23, INFO, "first mutable borrow occurs here"), false),
			new RustSubMessage(msg("src/main.rs", 8,19,8,23, ERROR, "second mutable borrow occurs here"), true),
			new RustSubMessage(msg("src/main.rs", 14,1,14,2, INFO, "first borrow ends here"), false)
		)
	);
	
	@Test
	public void test() throws Exception { test$(); }
	public void test$() throws Exception {
		// Test empty
		testParseMessage(
			"", 
			null, 
			list()
		);
		
		// Test simple message 2
		testParseMessage(
			getClassResource("rustc_error_simple.json"), 
			MSG_Simple, 
			list(
				msg(path("src/main.rs"), 8, 19, 8, 23, ERROR, "unresolved name `xpto`"+ " [E0425]:"+
					subm("unresolved name"))
			)
		);
		
		// Test message with notes
		String MSG_WithNotes_Main = "expected &MyTrait, found integral variable";
		String MSG_WithNotes_Top = "mismatched types";
		
		String MSG_MISMATCHED_A = MSG_WithNotes_Top+ " [E0308]:"+
			subm("expected type `&MyTrait`")+
			subm("   found type `{integer}`")+
			subm(MSG_WithNotes_Main);
		
		testParseMessage(
			getClassResource("rustc_error_with_notes.json"), 
			new RustMainMessage(
				new ToolSourceMessage(path(""), PARENT_MSG_RANGE, ERROR, MSG_WithNotes_Top),
				"E0308",
				list(
					"expected type `&MyTrait`",
					"   found type `{integer}`"
				),
				list(
					new RustSubMessage(msg("src/main.rs", 14, 9, 14, 12, ERROR, MSG_WithNotes_Main))
				)
			), 
			list(msg(path("src/main.rs"), 14, 9, 14, 12, ERROR, MSG_MISMATCHED_A))
		);
		
		// Test message with notes (span.label = null)
		String MSG_Main = "invalid fragment specifier `id`";
		String MSG_Notes = "valid fragment specifiers are...";
		testParseMessage(
			getClassResource("rustc_error_with_notes2.json"), 
			new RustMainMessage(
				new ToolSourceMessage(path(""), PARENT_MSG_RANGE, ERROR, MSG_Main),
				"",
				list(MSG_Notes),
				list(new RustSubMessage(msg("src/macro_tests.rs", 55,6,55,11, ERROR, "")))
			),
			list(
				msg(path("src/macro_tests.rs"), 55,6,55,11, ERROR, MSG_Main + ":\n" + MSG_Notes)
			)
		);
		
		
		// test composite
		testParseMessage(
			getClassResource("rustc_error_composite.json"), 
			MSG_CannotReborrow, 
			list(
				msg(path("src/main.rs"), 6, 19, 6, 23, INFO, "first mutable borrow occurs here"),
				msg(path("src/main.rs"), 8, 19, 8, 23, ERROR, CANNOT_BORROW_MUT_MORE_THAN + " [E0499]:" +
					subm("second mutable borrow occurs here")
				),
				msg(path("src/main.rs"), 14, 1, 14, 2, INFO, "first borrow ends here")
			)
		);
		
		
		// Test macros
		
		String spanMessage = "expected bool, found integral variable";
		String MSG_MISMATCHED_B = MSG_WithNotes_Top+ " [E0308]:"+
			subm("expected type `bool`")+
			subm("   found type `{integer}`");
		
		testParseMessage(
			getClassResource("rustc_error_macro.json"), 
			new RustMainMessage(
				new ToolSourceMessage(path(""), PARENT_MSG_RANGE, ERROR, MSG_WithNotes_Top),
				"E0308",
				list(
					"expected type `bool`",
					"   found type `{integer}`"
				),
				list(new RustSubMessage(
					msg("", 5,22,5,33, ERROR, spanMessage), 
					true, 
					new RustSubMessage(msg("src/main.rs", 15,5,15,26, ERROR, "")), 
					new RustSubMessage(msg("", 1,1,18,71, ERROR, ""))
				))
			),
			list(
				msg(path(""), 5,22,5,33, ERROR, MSG_MISMATCHED_B+subm(spanMessage)),
				msg(path("src/main.rs"), 15,5,15,26, INFO, MSG_MISMATCHED_B)
//				msg(path(""), 1,1,18,71, ERROR, MSG_MISMATCHED_B)
			)
		);
		
		// test and def_site_span = null, and code = null
		// Obtained with Rust code:
		/*
		   println!("{}");
		*/
		testParseMessage(
			getClassResource("rustc_error_macro_NullDefSiteSpan.json"), 
			new RustMainMessage(
				new ToolSourceMessage(path(""), PARENT_MSG_RANGE, ERROR, 
					"invalid reference to argument `0` (no arguments given)"),
				"",
				null,
				list(new RustSubMessage(
					msg(STD_MACROS_PATH, 1,33,1,58, ERROR, ""), 
					true, 
					new RustSubMessage(
						msg(STD_MACROS_PATH, 1,33,1,58, ERROR, ""),
						true,
						new RustSubMessage(
							msg(STD_MACROS_PATH, 2,27,2,58, ERROR, ""),
							true,
							new RustSubMessage(
								msg(STD_MACROS_PATH, 1,23,1,60, ERROR, ""),
								true,
								new RustSubMessage(msg("src/macro_tests.rs", 41,2,41,17, ERROR, "")),
								new RustSubMessage(msg(STD_MACROS_PATH, 1,1,3,58, ERROR, ""))
							),
							new RustSubMessage(msg(STD_MACROS_PATH, 1,1,2,64, ERROR, ""))
						),
						null
					), 
					null
				))
			),
			DONT_CHECK_ToolMessages
		);
	}
	
	public String subm(String subMessage) {
		return "\n"+subMessage;
	}
	
	public void testParseMessage(
		String messageJson, RustMessage expected, Indexable<ToolSourceMessage> expectedSourceMessages
	) throws CommonException 
	{
		RustJsonMessageParser msgParser = new RustJsonMessageParser();
		ArrayList2<RustMainMessage> rustMessages = msgParser.parseStructuredMessages(new StringReader(messageJson));
		if(expected == null) {
			assertTrue(rustMessages.isEmpty());
			return;
		}
		
		RustMainMessage message = unwrapSingle(rustMessages);
		checkEquals(message, expected);
		
		if(expectedSourceMessages != DONT_CHECK_ToolMessages) {
			assertEqualIndexable(message.retrieveToolMessages(), expectedSourceMessages);
		}
	}
	
	public void checkEquals(RustMessage message, RustMessage expected) {
		if(message == expected) {
			return;
		}
		if(!expected.equals(message)) {
			
			// Helper for the interactive debugger:
			assertAreEqual(message.sourceMessage, expected.sourceMessage);
			assertAreEqual(message.notes, expected.notes);
			checkIndexable(message.spans, expected.spans, this::checkEquals);
			if(message instanceof RustSubMessage && expected instanceof RustSubMessage) {
				checkSubMessage((RustSubMessage) message, (RustSubMessage) expected);
			}
			
			assertFail();
		}
	}
	
	public void checkSubMessage(RustSubMessage message, RustSubMessage expected) {
		checkEquals(message.defSiteMsg, expected.defSiteMsg);
		checkEquals(message.expansionMsg, expected.expansionMsg);
	}
	
}