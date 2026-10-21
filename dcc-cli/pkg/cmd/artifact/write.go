package artifact

import (
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/api"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/confirmation"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/filedata"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/output"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

type createOptions struct {
	EnvironmentID int64
	ApplicationID int64
	FromFile      string
	Code          string
	Category      string
	InterfaceNo   string
	TemplateID    int64
	Message       cmdutil.MessageOptions
}

// NewCmdCreate returns the noun's create command.
func NewCmdCreate(f *factory.Factory, kind Kind) *cobra.Command {
	opts := &createOptions{}
	cmd := &cobra.Command{
		Use:   "create",
		Short: "Create " + article(kind) + " " + kind.Noun + " from a JSON/YAML file",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			if opts.FromFile == "" {
				return cmdutil.UsageError("--from-file is required")
			}
			if opts.EnvironmentID <= 0 || opts.ApplicationID <= 0 {
				return cmdutil.UsageError("--environment-id and --application-id are required")
			}
			message, err := opts.Message.Resolve(f.IOStreams)
			if err != nil {
				return err
			}
			body, err := filedata.Load(opts.FromFile)
			if err != nil {
				return cmdutil.Failure("%s", err)
			}
			applyCreateOverrides(body, opts)
			if err := requireCreateFields(body, kind); err != nil {
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

			query := url.Values{
				"environmentId": {strconv.FormatInt(opts.EnvironmentID, 10)},
				"applicationId": {strconv.FormatInt(opts.ApplicationID, 10)},
			}
			var response any
			err = client.Do(cmd.Context(), api.Request{
				Method:        http.MethodPost,
				Path:          client.APIPath(kind.APIPath),
				Query:         query,
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
	cmd.Flags().Int64Var(&opts.EnvironmentID, "environment-id", 0, "Target environment id")
	cmd.Flags().Int64Var(&opts.ApplicationID, "application-id", 0, "Target application id")
	cmd.Flags().StringVarP(&opts.FromFile, "from-file", "f", "", "JSON/YAML request body (\"-\" = stdin)")
	cmd.Flags().StringVar(&opts.Code, "code", "", "Override the code field")
	cmd.Flags().StringVar(&opts.Category, "category", "", "Override the category field")
	cmd.Flags().StringVar(&opts.InterfaceNo, "interface-no", "", "Override the interface number")
	cmd.Flags().Int64Var(&opts.TemplateID, "template-id", 0, "Override the template id")
	cmdutil.AddMessageFlags(cmd, &opts.Message)
	_ = cmd.MarkFlagRequired("from-file")
	return cmd
}

func applyCreateOverrides(body map[string]any, opts *createOptions) {
	if opts.Code != "" {
		body["code"] = opts.Code
	}
	if opts.Category != "" {
		body["category"] = opts.Category
	}
	if opts.InterfaceNo != "" {
		body["interfaceNo"] = opts.InterfaceNo
	}
	if opts.TemplateID > 0 {
		body["templateId"] = opts.TemplateID
	}
}

func requireCreateFields(body map[string]any, kind Kind) error {
	if _, ok := body["content"]; !ok || body["content"] == nil {
		return fmt.Errorf("missing required field %q", "content")
	}
	if kind.Noun == "interface" {
		_, err := filedata.RequireString(body, "interfaceNo")
		return err
	}
	_, err := filedata.RequireString(body, "code")
	return err
}

type editOptions struct {
	FromFile string
	Message  cmdutil.MessageOptions
}

// NewCmdEdit returns the noun's edit command.
func NewCmdEdit(f *factory.Factory, kind Kind) *cobra.Command {
	opts := &editOptions{}
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
			if _, ok := body["content"]; !ok || body["content"] == nil {
				return cmdutil.UsageError("missing required field %q", "content")
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

type statusOptions struct {
	Target  string
	Message cmdutil.MessageOptions
}

// NewCmdStatus returns the noun's status transition command.
func NewCmdStatus(f *factory.Factory, kind Kind) *cobra.Command {
	opts := &statusOptions{}
	cmd := &cobra.Command{
		Use:   "status <id>",
		Short: "Transition the lifecycle status of " + article(kind) + " " + kind.Noun,
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
				Path:          client.APIPath(fmt.Sprintf("%s/%d/status", kind.APIPath, id)),
				Query:         url.Values{"target": {opts.Target}},
				ChangeMessage: message.HeaderValue(),
				Out:           &response,
			})
			if err != nil {
				return cmdutil.WrapError(err)
			}
			return printMutation(out, f, "transitioned", kind, strconv.FormatInt(id, 10), response)
		},
	}
	cmd.Flags().StringVar(&opts.Target, "target", "", "Target status")
	cmdutil.AddMessageFlags(cmd, &opts.Message)
	_ = cmd.MarkFlagRequired("target")
	return cmd
}

type rollbackOptions struct {
	Version string
	Yes     bool
	Message cmdutil.MessageOptions
}

// NewCmdRollback returns the noun's rollback command.
func NewCmdRollback(f *factory.Factory, kind Kind) *cobra.Command {
	opts := &rollbackOptions{}
	cmd := &cobra.Command{
		Use:   "rollback <id>",
		Short: "Roll " + article(kind) + " " + kind.Noun + " back to an earlier version",
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
				fmt.Sprintf("Roll back %s #%d to %s?", kind.Noun, id, opts.Version), opts.Yes); err != nil {
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
				Path:          client.APIPath(fmt.Sprintf("%s/%d/rollback", kind.APIPath, id)),
				Query:         url.Values{"version": {opts.Version}},
				ChangeMessage: message.HeaderValue(),
				Out:           &response,
			})
			if err != nil {
				return cmdutil.WrapError(err)
			}
			return printMutation(out, f, "rolled back", kind, strconv.FormatInt(id, 10), response)
		},
	}
	cmd.Flags().StringVar(&opts.Version, "version", "", "Version to restore")
	cmd.Flags().BoolVar(&opts.Yes, "yes", false, "Skip the confirmation prompt")
	cmdutil.AddMessageFlags(cmd, &opts.Message)
	_ = cmd.MarkFlagRequired("version")
	return cmd
}

type deleteOptions struct {
	Yes     bool
	Message cmdutil.MessageOptions
}

// NewCmdDelete returns the noun's delete command.
func NewCmdDelete(f *factory.Factory, kind Kind) *cobra.Command {
	opts := &deleteOptions{}
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

type restoreOptions struct {
	Yes     bool
	Message cmdutil.MessageOptions
}

// NewCmdRestore returns the noun's restore command.
func NewCmdRestore(f *factory.Factory, kind Kind) *cobra.Command {
	opts := &restoreOptions{}
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

func responseID(response any) string {
	object, ok := response.(map[string]any)
	if !ok {
		return ""
	}
	return formatValue(object["id"])
}

func printMutation(out output.Options, f *factory.Factory, verb string, kind Kind, id string, response any) error {
	return out.Print(f.IOStreams.Out, response, func(w io.Writer) error {
		label := ""
		status := ""
		if object, ok := response.(map[string]any); ok {
			label = formatValue(object["code"])
			if kind.Noun == "interface" {
				label = formatValue(object["interfaceNo"])
			}
			status = formatValue(object["status"])
		}
		_, err := fmt.Fprintf(w, "%s %s", verb, kind.Noun)
		if err != nil {
			return err
		}
		if label != "" {
			fmt.Fprintf(w, " %s", label)
		}
		if id != "" {
			fmt.Fprintf(w, " #%s", id)
		}
		if status != "" {
			fmt.Fprintf(w, " (%s)", status)
		}
		_, err = fmt.Fprintln(w)
		return err
	})
}
