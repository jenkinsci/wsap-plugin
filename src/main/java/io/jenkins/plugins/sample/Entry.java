package io.jenkins.plugins.sample;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;

public abstract class Entry extends AbstractDescribableImpl<Entry> implements ExtensionPoint,ConsoleSupport{}