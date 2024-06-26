package org.nypl.simplified.tests.books.controller;

import android.content.Context;

import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProfilesControllerTest extends ProfilesControllerContract {

  @Override
  protected Context context() {
    return Mockito.mock(Context.class);
  }


  @Override
  protected Logger getLogger() {
    return LoggerFactory.getLogger(ProfilesControllerTest.class);
  }
}
