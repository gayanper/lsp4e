/*******************************************************************************
 * Copyright (c) 2017, 2020 Pivotal Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  - Martin Lippert (Pivotal Inc.) - Initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.jdt;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.operations.completion.LSContentAssistProcessor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

@SuppressWarnings({ "restriction" })
public class LSJavaCompletionProposalComputer implements IJavaCompletionProposalComputer {
	
	private static TimeUnit TIMEOUT_UNIT = TimeUnit.MILLISECONDS;
	private static long TIMEOUT_LENGTH = 300;

	private LSContentAssistProcessor lsContentAssistProcessor;
	private String javaCompletionSpecificErrorMessage;

	public LSJavaCompletionProposalComputer() {
		lsContentAssistProcessor = new LSContentAssistProcessor(false);
	}

	@Override
	public void sessionStarted() {
	}

	@Override
	public List<ICompletionProposal> computeCompletionProposals(ContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		CompletableFuture<ICompletionProposal[]> future = CompletableFuture.supplyAsync(() -> {
			return lsContentAssistProcessor.computeCompletionProposals(context.getViewer(), context.getInvocationOffset());
		});
		
		try {
			return Arrays.asList(asJavaProposals(future));
		} catch (TimeoutException e) {
			LanguageServerPlugin.logError(e);
			javaCompletionSpecificErrorMessage = createErrorMessage(e);
			return Collections.emptyList();
		} catch (ExecutionException e) {
			LanguageServerPlugin.logError(e);
			javaCompletionSpecificErrorMessage = createErrorMessage(e);
			return Collections.emptyList();
		} catch (InterruptedException e) {
			LanguageServerPlugin.logError(e);
			javaCompletionSpecificErrorMessage = createErrorMessage(e);
			Thread.currentThread().interrupt();
			return Collections.emptyList();
		}
	}

	private String createErrorMessage(Exception ex) {
		return Messages.javaSpecificCompletionError + " : " + (ex.getMessage() != null ? ex.getMessage() : ex.toString()); //$NON-NLS-1$
	}

	/**
	 * In order for the LS proposals to appear in the right order by JDT, we need to return IJavaCompletionProposal.
	 * The LSPCompletionProposal that LSP4E computes is NOT IJavaCompletionProposal, and as a consequence JDT
	 * will by default sort any non Java proposals by display value, which is why we would get a strange sorting order,
	 * even if our the LS and LSP4E both return a proposal list in the right order.
	 * 
	 * This method wraps around the LSCompletionProposal with a IJavaCompletionProposal, and it sets the relevance
	 * number that JDT uses to sort proposals in a desired order.
	 */
	private ICompletionProposal[] asJavaProposals(CompletableFuture<ICompletionProposal[]> future)
			throws InterruptedException, ExecutionException, TimeoutException {
		ICompletionProposal[] originalProposals = future.get(TIMEOUT_LENGTH, TIMEOUT_UNIT);
		
		// We assume that the original proposals are in the correct order, so we set relevance
		// based on this existing order. Note that based on IJavaCompletionProposal javadoc,
		// relevance values are [0,1000] so we start at 1000
		int relevance = 1000;
		ICompletionProposal[] javaProposals = new ICompletionProposal[originalProposals.length];
		
		for (int i = 0; i < originalProposals.length; i++) {
			javaProposals[i] = new LSJavaProposal(originalProposals[i], relevance--);
		}
		 
		return javaProposals;
	}

	@Override
	public List<IContextInformation> computeContextInformation(ContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		IContextInformation[] contextInformation = lsContentAssistProcessor.computeContextInformation(context.getViewer(), context.getInvocationOffset());
		return Arrays.asList(contextInformation);
	}
	
	@Override
	public String getErrorMessage() {
		return javaCompletionSpecificErrorMessage != null ? javaCompletionSpecificErrorMessage : lsContentAssistProcessor.getErrorMessage();
	}

	@Override
	public void sessionEnded() {
	}
	
	class LSJavaProposal implements IJavaCompletionProposal {
		
		private ICompletionProposal delegate;
		private int relevance;

		public LSJavaProposal(ICompletionProposal delegate,  int relevance) {
			this.delegate = delegate;
			this.relevance = relevance;
		}

		@Override
		public void apply(IDocument document) {
			delegate.apply(document);	
		}

		@Override
		public String getAdditionalProposalInfo() {
			return delegate.getAdditionalProposalInfo();
		}

		@Override
		public IContextInformation getContextInformation() {
			return delegate.getContextInformation();
		}

		@Override
		public String getDisplayString() {
			return delegate.getDisplayString();
		}

		@Override
		public Image getImage() {
			return delegate.getImage();
		}

		@Override
		public Point getSelection(IDocument document) {
			return delegate.getSelection(document);
		}

		@Override
		public int getRelevance() {
			return relevance;
		}
		
	}

}
