import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { BottomSheet } from "./sheet";

describe("BottomSheet", () => {
  it("is a labelled region whose handle expands and collapses it", async () => {
    render(<BottomSheet label="Your ride" peek={300}><p>Driver on the way</p></BottomSheet>);

    expect(screen.getByRole("region", { name: "Your ride" })).toBeInTheDocument();
    expect(screen.getByText("Driver on the way")).toBeInTheDocument();
    const handle = screen.getByRole("button", { name: "Expand ride panel" });
    expect(handle).toHaveAttribute("aria-expanded", "false");

    await userEvent.click(handle);
    expect(screen.getByRole("button", { name: "Collapse ride panel" })).toHaveAttribute("aria-expanded", "true");
  });
});
