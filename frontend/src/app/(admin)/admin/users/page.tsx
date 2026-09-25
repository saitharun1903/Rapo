"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Search } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { ReasonDialog } from "@/components/admin/ReasonDialog";
import { Button } from "@/components/ui/button";
import { Field, Input, Select } from "@/components/ui/form";
import { Pager } from "@/components/ui/pager";
import { Badge, EmptyState, ErrorState, LoadingBlock, PageHeader } from "@/components/ui/surface";
import { Table, Td, Th, Tr } from "@/components/ui/table";
import { api, unwrap } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/errors";
import type { Role, UserResponse, UserStatus } from "@/lib/api/types";
import { formatDateTime, humanize } from "@/lib/format";
import { queryKeys } from "@/lib/queryKeys";

const PAGE_SIZE = 20;
const ROLES: Role[] = ["PASSENGER", "DRIVER", "ADMIN"];
const STATUSES: UserStatus[] = ["ACTIVE", "SUSPENDED"];

export default function AdminUsersPage() {
  const queryClient = useQueryClient();
  const [text, setText] = useState("");
  const [q, setQ] = useState("");
  const [role, setRole] = useState<Role | "">("");
  const [status, setStatus] = useState<UserStatus | "">("");
  const [page, setPage] = useState(0);
  const [target, setTarget] = useState<UserResponse | null>(null);
  const query = { q: q === "" ? undefined : q, role: role === "" ? undefined : role, status: status === "" ? undefined : status, page, size: PAGE_SIZE };
  const users = useQuery({
    queryKey: queryKeys.admin.users(JSON.stringify(query)),
    queryFn: () => unwrap(api.GET("/api/admin/users", { params: { query } })),
    placeholderData: keepPreviousData,
  });
  const change = useMutation({
    mutationFn: ({ user, reason }: { user: UserResponse; reason: string }) => unwrap(api.PATCH("/api/admin/users/{userId}/status", {
      params: { path: { userId: user.id } },
      body: { status: user.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE", reason },
    })),
    onSuccess: (user) => {
      toast.success(`${user.fullName} is ${user.status.toLowerCase()}.`);
      setTarget(null);
      void queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const suspending = target?.status === "ACTIVE";

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <PageHeader title="Users" description="Search accounts and suspend or reactivate them. Suspension signs the user out everywhere." />
      <form className="mb-4 grid gap-3 sm:grid-cols-[1fr_12rem_12rem_auto] sm:items-end" role="search"
        onSubmit={(event) => { event.preventDefault(); setQ(text.trim()); setPage(0); }}>
        <Field label="Name or email">{({ id }) => <Input id={id} value={text} onChange={(event) => setText(event.target.value)} />}</Field>
        <Field label="Role">
          {({ id }) => (
            <Select id={id} value={role} onChange={(event) => { setRole(event.target.value as Role | ""); setPage(0); }}>
              <option value="">Any role</option>
              {ROLES.map((value) => <option key={value} value={value}>{humanize(value)}</option>)}
            </Select>
          )}
        </Field>
        <Field label="Status">
          {({ id }) => (
            <Select id={id} value={status} onChange={(event) => { setStatus(event.target.value as UserStatus | ""); setPage(0); }}>
              <option value="">Any status</option>
              {STATUSES.map((value) => <option key={value} value={value}>{humanize(value)}</option>)}
            </Select>
          )}
        </Field>
        <Button type="submit"><Search className="size-4" aria-hidden /> Search</Button>
      </form>
      {users.isPending && <LoadingBlock label="Loading users" rows={5} />}
      {users.isError && <ErrorState error={users.error} onRetry={() => void users.refetch()} />}
      {users.data && users.data.content.length === 0 && <EmptyState title="No users match" />}
      {users.data && users.data.content.length > 0 && (
        <>
          <Table caption="Users">
            <thead><tr><Th>Name</Th><Th>Email</Th><Th>Role</Th><Th>Status</Th><Th>Joined</Th><Th className="text-right">Actions</Th></tr></thead>
            <tbody>
              {users.data.content.map((user) => (
                <Tr key={user.id}>
                  <Td className="font-medium">{user.fullName}</Td>
                  <Td>{user.email}</Td>
                  <Td>{humanize(user.role)}</Td>
                  <Td><Badge tone={user.status === "ACTIVE" ? "success" : "danger"}>{humanize(user.status)}</Badge></Td>
                  <Td>{formatDateTime(user.createdAt)}</Td>
                  <Td className="text-right">
                    {user.role !== "ADMIN" && (
                      <Button size="sm" variant="secondary" onClick={() => setTarget(user)}>
                        {user.status === "ACTIVE" ? "Suspend" : "Reactivate"}
                      </Button>
                    )}
                  </Td>
                </Tr>
              ))}
            </tbody>
          </Table>
          <Pager page={page} totalPages={users.data.totalPages} onChange={setPage} />
        </>
      )}
      <ReasonDialog open={target !== null} pending={change.isPending} onClose={() => setTarget(null)}
        title={`${suspending ? "Suspend" : "Reactivate"} ${target?.fullName ?? ""}?`}
        description={suspending ? "All their sessions end now; a driver is also taken offline." : "They can sign in again."}
        confirmLabel={suspending ? "Suspend" : "Reactivate"}
        onConfirm={(reason) => target && change.mutate({ user: target, reason })} />
    </div>
  );
}
