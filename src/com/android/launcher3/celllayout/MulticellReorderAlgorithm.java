/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.celllayout;

import android.view.View;

import com.android.launcher3.CellLayout;
import com.android.launcher3.MultipageCellLayout;
import com.android.launcher3.util.GridOccupancy;

import java.util.function.Supplier;

/**
 * Variant of ReorderAlgorithm which simulates a foldable screen and adds a seam in the middle
 * to prevent items to be placed in the middle.
 */
public class MulticellReorderAlgorithm extends ReorderAlgorithm {

    private final View mSeam;

    public MulticellReorderAlgorithm(CellLayout cellLayout) {
        super(cellLayout);
        mSeam = new View(cellLayout.getContext());
    }

    private CellLayout.ItemConfiguration removeSeamFromSolution(
            CellLayout.ItemConfiguration solution) {
        solution.map.forEach((view, cell) -> cell.cellX =
                cell.cellX > mCellLayout.getCountX() / 2 ? cell.cellX - 1 : cell.cellX);
        solution.cellX =
                solution.cellX > mCellLayout.getCountX() / 2 ? solution.cellX - 1 : solution.cellX;
        return solution;
    }

    @Override
    public CellLayout.ItemConfiguration closestEmptySpaceReorder(int pixelX, int pixelY,
                                                                 int minSpanX, int minSpanY,
                                                                 int spanX, int spanY) {
        return removeSeamFromSolution(simulateSeam(
                () -> super.closestEmptySpaceReorder(pixelX, pixelY, minSpanX, minSpanY, spanX,
                        spanY)));
    }

    @Override
    public CellLayout.ItemConfiguration findReorderSolution(int pixelX, int pixelY, int minSpanX,
                                                            int minSpanY, int spanX, int spanY, int[] direction, View dragView, boolean decX,
                                                            CellLayout.ItemConfiguration solution) {
        return removeSeamFromSolution(simulateSeam(
                () -> super.findReorderSolution(pixelX, pixelY, minSpanX, minSpanY, spanX, spanY,
                        direction, dragView, decX, solution)));
    }

    @Override
    public CellLayout.ItemConfiguration dropInPlaceSolution(int pixelX, int pixelY, int spanX,
                                                            int spanY,
                                                            View dragView) {
        return removeSeamFromSolution(simulateSeam(
                () -> super.dropInPlaceSolution(pixelX, pixelY, spanX, spanY, dragView)));
    }

    void addSeam() {
        MultipageCellLayout mcl = (MultipageCellLayout) mCellLayout;
        mcl.setSeamWasAdded(true);
        CellLayoutLayoutParams lp = new CellLayoutLayoutParams(mcl.getCountX() / 2, 0, 1,
                mcl.getCountY());
        lp.canReorder = false;
        mcl.setCountX(mcl.getCountX() + 1);
        mcl.getShortcutsAndWidgets().addViewInLayout(mSeam, lp);
        mcl.setOccupied(createGridOccupancyWithSeam(mcl.getOccupied()));
        mcl.mTmpOccupied = new GridOccupancy(mcl.getCountX(), mcl.getCountY());
    }

    void removeSeam() {
        MultipageCellLayout mcl = (MultipageCellLayout) mCellLayout;
        mcl.setCountX(mcl.getCountX() - 1);
        mcl.getShortcutsAndWidgets().removeViewInLayout(mSeam);
        mcl.mTmpOccupied = new GridOccupancy(mcl.getCountX(), mcl.getCountY());
        mcl.setSeamWasAdded(false);
    }

    /**
     * The function supplied here will execute while the CellLayout has a simulated seam added.
     *
     * @param f   function to run under simulation
     * @param <T> return value of the supplied function
     * @return Value of supplied function
     */
    public <T> T simulateSeam(Supplier<T> f) {
        MultipageCellLayout mcl = (MultipageCellLayout) mCellLayout;
        if (mcl.isSeamWasAdded()) {
            return f.get();
        }
        GridOccupancy auxGrid = mcl.getOccupied();
        addSeam();
        T res = f.get();
        removeSeam();
        mcl.setOccupied(auxGrid);
        return res;
    }

    GridOccupancy createGridOccupancyWithSeam(GridOccupancy gridOccupancy) {
        GridOccupancy grid = new GridOccupancy(mCellLayout.getCountX(), mCellLayout.getCountY());
        for (int x = 0; x < mCellLayout.getCountX(); x++) {
            for (int y = 0; y < mCellLayout.getCountY(); y++) {
                int offset = x >= mCellLayout.getCountX() / 2 ? 1 : 0;
                if (x == mCellLayout.getCountX() / 2) {
                    grid.cells[x][y] = true;
                } else {
                    grid.cells[x][y] = gridOccupancy.cells[x - offset][y];
                }
            }
        }
        return grid;
    }
}
