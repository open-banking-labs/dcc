package tenantresource

import (
	"fmt"
	"io"
	"net/http"
	"strconv"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/api"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/confirmation"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/filedata"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/output"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

type writeOptions struct {
	FromFile string
	Message  cmdutil.MessageOptions
	Yes      bool
}

// NewCmdCreate returns the create command.
func NewCmdCreate(f *factory.Factory, kind Kind) *cobra.Command {
	opts := &writeOptions{}
	cmd := &cobra.Command{
		Use:   "create",
		Short: "Create " + article(kind) + " " + kind.Noun + " from a JSON/YAML file",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			if opts.FromFile == "" {
				return cmdutil.UsageError("--from-file is required")
			}
			message, err := opts.Message.Resolve(f.IOStreams)
			if err != nil {
				return err
			}
			body, err := filedata.Load(opts.FromFile)
			if err != nil {
				return cmdutil.Failure("%s", err)
			}
			if _, err := filedata.RequireString(body, "code"); err != nil {
				return cmdutil.UsageError("%s", err)
			}
			if _, err := filedata.RequireString(body, "name"); err != nil {
				return cmdutil.UsageError("%s", err)
			}

			out, err := output.FromCommand(cmd)
			if err != nil {
				return err
			}
			client, _, err := cmdutil.NewClient(cmd, f)
			if err != nil {
				return err
			}

			var response any
			err = client.Do(cmd.Context(), api.Request{
				Method:        http.MethodPost,
				Path:          client.APIPath(kind.APIPath),
				Body:          body,
				ChangeMessage: message.HeaderValue(),
				Out:           &response,
			})
			if err != nil {
				return cmdutil.WrapError(err)
			}
			return printMutation(out, f, "created", kind, responseID(response), response)
		},
	}
	cmd.Flags().StringVarP(&opts.FromFile, "from-file", "f", "", "JSON/YAML request body (\"-\" = stdin)")
	cmdutil.AddMessageFlags(cmd, &opts.Message)
	_ = cmd.MarkFlagRequired("from-file")
	return cmd
}

// NewCmdEdit returns the edit command.
func NewCmdEdit(f *factory.Factory, kind Kind) *cobra.Command {
	opts := &writeOptions{}
	cmd := &cobra.Command{
		Use:   "edit <id>",
		Short: "Update " + article(kind) + " " + kind.Noun + " from a JSON/YAML file",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			id, err := parseID(args)
			if err != nil {
				return err
			}
			if opts.FromFile == "" {
				return cmdutil.UsageError("--from-file is required")
			}
			message, err := opts.Message.Resolve(f.IOStreams)
			if err != nil {
				return err
			}
			body, err := filedata.Load(opts.FromFile)
			if err != nil {
				return cmdutil.Failure("%s", err)
			}

			out, err := output.FromCommand(cmd)
			if err != nil {
				return err
			}
			client, _, err := cmdutil.NewClient(cmd, f)
			if err != nil {
				return err
			}

			var response any
			err = client.Do(cmd.Context(), api.Request{
				Method:        http.MethodPut,
				Path:          client.APIPath(fmt.Sprintf("%s/%d", kind.APIPath, id)),
				Body:          body,
				ChangeMessage: message.HeaderValue(),
				Out:           &response,
			})
			if err != nil {
				return cmdutil.WrapError(err)
			}
			return printMutation(out, f, "updated", kind, strconv.FormatInt(id, 10), response)
		},
	}
	cmd.Flags().StringVarP(&opts.FromFile, "from-file", "f", "", "JSON/YAML request body (\"-\" = stdin)")
	cmdutil.AddMessageFlags(cmd, &opts.Message)
	_ = cmd.MarkFlagRequired("from-file")
	return cmd
}

// NewCmdDelete returns the delete command.
func NewCmdDelete(f *factory.Factory, kind Kind) *cobra.Command {
	opts := &writeOptions{}
	cmd := &cobra.Command{
		Use:   "delete <id>",
		Short: "Logically delete " + article(kind) + " " + kind.Noun,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			id, err := parseID(args)
			if err != nil {
				return err
			}
			message, err := opts.Message.Resolve(f.IOStreams)
			if err != nil {
				return err
			}
			if err := confirmation.Confirm(f.IOStreams,
				fmt.Sprintf("Delete %s #%d?", kind.Noun, id), opts.Yes); err != nil {
				return err
			}
			out, err := output.FromCommand(cmd)
			if err != nil {
				return err
			}
			client, _, err := cmdutil.NewClient(cmd, f)
			if err != nil {
				return err
			}

			err = client.Do(cmd.Context(), api.Request{
				Method:        http.MethodDelete,
				Path:          client.APIPath(fmt.Sprintf("%s/%d", kind.APIPath, id)),
				ChangeMessage: message.HeaderValue(),
			})
			if err != nil {
				return cmdutil.WrapError(err)
			}
			response := map[string]any{"deleted": true, "id": id}
			return printMutation(out, f, "deleted", kind, strconv.FormatInt(id, 10), response)
		},
	}
	cmd.Flags().BoolVar(&opts.Yes, "yes", false, "Skip the confirmation prompt")
	cmdutil.AddMessageFlags(cmd, &opts.Message)
	return cmd
}

// NewCmdRestore returns the restore command.
func NewCmdRestore(f *factory.Factory, kind Kind) *cobra.Command {
	opts := &writeOptions{}
	cmd := &cobra.Command{
		Use:   "restore <id>",
		Short: "Restore a logically-deleted " + kind.Noun,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			id, err := parseID(args)
			if err != nil {
				return err
			}
			message, err := opts.Message.Resolve(f.IOStreams)
			if err != nil {
				return err
			}
			if err := confirmation.Confirm(f.IOStreams,
				fmt.Sprintf("Restore %s #%d?", kind.Noun, id), opts.Yes); err != nil {
				return err
			}
			out, err := output.FromCommand(cmd)
			if err != nil {
				return err
			}
			client, _, err := cmdutil.NewClient(cmd, f)
			if err != nil {
				return err
			}

			var response any
			err = client.Do(cmd.Context(), api.Request{
				Method:        http.MethodPost,
				Path:          client.APIPath(fmt.Sprintf("%s/%d/restore", kind.APIPath, id)),
				ChangeMessage: message.HeaderValue(),
				Out:           &response,
			})
			if err != nil {
				return cmdutil.WrapError(err)
			}
			return printMutation(out, f, "restored", kind, strconv.FormatInt(id, 10), response)
		},
	}
	cmd.Flags().BoolVar(&opts.Yes, "yes", false, "Skip the confirmation prompt")
	cmdutil.AddMessageFlags(cmd, &opts.Message)
	return cmd
}

func printMutation(out output.Options, f *factory.Factory, verb string, kind Kind, id string, response any) error {
	return out.Print(f.IOStreams.Out, response, func(w io.Writer) error {
		label := ""
		if object, ok := response.(map[string]any); ok {
			label = formatValue(object["code"])
		}
		if _, err := fmt.Fprintf(w, "%s %s", verb, kind.Noun); err != nil {
			return err
		}
		if label != "" {
			fmt.Fprintf(w, " %s", label)
		}
		if id != "" {
			fmt.Fprintf(w, " #%s", id)
		}
		_, err := fmt.Fprintln(w)
		return err
	})
}
