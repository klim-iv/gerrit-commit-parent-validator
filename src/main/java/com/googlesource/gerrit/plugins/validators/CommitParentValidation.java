package com.googlesource.gerrit.plugins.validators;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import com.google.gerrit.extensions.annotations.Listen;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Listen
@Singleton
public class CommitParentValidation implements CommitValidationListener {
	private final static String COMMIT_CHECK_SECTION = "commitcheck";
	private final static String REJECT_PARENT_NOT_HEAD = "rejectParentNotHead";

	private final Config config;
	private boolean rejectParentNotHead;

	@Inject
	private GitRepositoryManager gitRepositoryManager;

	@Inject
	public CommitParentValidation(@GerritServerConfig Config gerritConfig)
			throws ConfigInvalidException, IOException {
		this.config = gerritConfig;
		this.rejectParentNotHead = config.getBoolean(COMMIT_CHECK_SECTION,
				REJECT_PARENT_NOT_HEAD, false);
	}

	@Override
	public List<CommitValidationMessage> onCommitReceived(
			CommitReceivedEvent receiveEvent) throws CommitValidationException {
		final RevCommit commit = receiveEvent.commit;
		List<CommitValidationMessage> messages = new ArrayList<CommitValidationMessage>();
		Repository repo = null;
		boolean isOK = false;

		try {
			repo = gitRepositoryManager.openRepository(receiveEvent.project.getNameKey());
			ObjectId headCommit = repo.resolve(receiveEvent.refName);
			ObjectId parentCommit = commit.getParent(0).toObjectId();

			isOK = headCommit.equals(parentCommit);

			if (!isOK) {
				messages.add(new CommitValidationMessage("(W) "
						+ "!!! PLEASE UPDATE REPO AND REBASE YOUR COMMIT !!!", false));
				messages.add(new CommitValidationMessage("(W) "
						+ "Server-side HEAD-Id (" + headCommit.name() + ")"
						+ " != "
						+ "Parent-Id for this commit (" + parentCommit.name() + ")", false));
			}
		} catch (Exception ex) {
			messages.add(new CommitValidationMessage("(W2) " + ex.getMessage(),
					false));
		}

		if (!isOK && rejectParentNotHead)
			throw new CommitValidationException(
					"Commit parent validation failed", messages);

		return messages;
	}
}
