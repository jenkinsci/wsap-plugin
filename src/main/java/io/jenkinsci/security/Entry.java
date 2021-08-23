package io.jenkinsci.security;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;

public abstract class Entry extends AbstractDescribableImpl<Entry> implements ExtensionPoint,ConsoleSupport{}