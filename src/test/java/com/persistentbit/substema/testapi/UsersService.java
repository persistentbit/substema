package com.persistentbit.substema.testapi;

import com.persistentbit.core.collections.PList;
import com.persistentbit.substema.annotations.Remotable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * @author Peter Muys
 * @since 2/09/2016
 */
@Remotable
public interface UsersService {
    CompletableFuture<PList<UserData>> getAllUsers();
    CompletableFuture<Optional<Integer>> testOptional(Integer value);
}